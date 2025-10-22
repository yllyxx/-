package com.example.acupunctureinstrument;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class DeviceListActivity extends AppCompatActivity {

    private static final String TAG = "DeviceListActivity";
    private static final boolean D = true;  // Debug开关

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_LOCATION = 2;

    // 标准的SPP（串口）UUID，大多数蓝牙模块都使用这个
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private DeviceListAdapter pairedDevicesAdapter;
    private DeviceListAdapter newDevicesAdapter;
    private ArrayList<BluetoothDevice> pairedDevicesList;
    private ArrayList<BluetoothDevice> newDevicesList;

    private Button btnScan;
    private ProgressBar progressBar;
    private TextView tvNewDevicesTitle;
    private ProgressDialog connectingDialog;

    // 连接相关
    private BluetoothSocket bluetoothSocket;
    private ConnectThread connectThread;
    //标志位，是否有用户主动发起的扫描
    private boolean isUserInitiatedScan = false;

    // Handler处理连接结果
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1: // 连接成功
                    if (connectingDialog != null && connectingDialog.isShowing()) {
                        connectingDialog.dismiss();
                    }

                    BluetoothDevice device = (BluetoothDevice) msg.obj;

                    // 不要在这里调用 BluetoothConnection.getInstance().connected()
                    // 只传递设备信息回去
                    Intent intent = new Intent();
                    intent.putExtra("device_address", device.getAddress());
                    intent.putExtra("device_name", device.getName());
                    intent.putExtra("connected", false); // 改为false，让MainActivity重新连接
                    intent.putExtra("connection_type", "SPP");

                    // 关闭这里建立的连接
                    if (bluetoothSocket != null) {
                        try {
                            bluetoothSocket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Close socket failed", e);
                        }
                    }

                    connectThread = null;
                    setResult(RESULT_OK, intent);
                    finish();
                    break;

                case 2: // 连接失败
                    if (connectingDialog != null && connectingDialog.isShowing()) {
                        connectingDialog.dismiss();
                    }
                    String error = (String) msg.obj;
                    Toast.makeText(DeviceListActivity.this,
                            "连接失败：" + error, Toast.LENGTH_LONG).show();
                    break;

                case 3: // BLE连接成功
                    if (connectingDialog != null && connectingDialog.isShowing()) {
                        connectingDialog.dismiss();
                    }

                    BluetoothDevice bleDevice = (BluetoothDevice) msg.obj;
                    Toast.makeText(DeviceListActivity.this, "BLE连接成功", Toast.LENGTH_SHORT).show();

                    // 将BLE连接信息传回MainActivity
                    Intent bleIntent = new Intent();
                    bleIntent.putExtra("device_address", bleDevice.getAddress());
                    bleIntent.putExtra("device_name", bleDevice.getName());
                    bleIntent.putExtra("connected", true);
                    bleIntent.putExtra("connection_type", "BLE"); // 标记为BLE连接

                    setResult(RESULT_OK, bleIntent);
                    finish();
                    break;
            }
        }
    };

    // 广播接收器，用于接收发现的设备
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getName() != null) {
                    // 检查是否已经在列表中
                    boolean isInPairedList = false;
                    for (BluetoothDevice d : pairedDevicesList) {
                        if (d.getAddress().equals(device.getAddress())) {
                            isInPairedList = true;
                            break;
                        }
                    }

                    if (!isInPairedList) {
                        boolean isInNewList = false;
                        for (BluetoothDevice d : newDevicesList) {
                            if (d.getAddress().equals(device.getAddress())) {
                                isInNewList = true;
                                break;
                            }
                        }

                        if (!isInNewList) {
                            newDevicesList.add(device);
                            newDevicesAdapter.notifyDataSetChanged();
                            tvNewDevicesTitle.setVisibility(View.VISIBLE);
                            findViewById(R.id.card_new_devices).setVisibility(View.VISIBLE);
                        }
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                progressBar.setVisibility(View.VISIBLE);
                btnScan.setEnabled(false);
                btnScan.setText("扫描中...");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);
                btnScan.setText("扫描设备");

                // 只有在用户主动扫描且没有发现新设备时才显示提示
                if (isUserInitiatedScan && newDevicesList.isEmpty()) {
                    Toast.makeText(DeviceListActivity.this, "未发现新设备", Toast.LENGTH_SHORT).show();
                }
                isUserInitiatedScan = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        // 初始化视图
        initViews();

        // 初始化蓝牙
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 检查蓝牙是否开启
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            setupDeviceLists();
        }

        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);
    }

    private void initViews() {
        btnScan = findViewById(R.id.btn_scan);
        progressBar = findViewById(R.id.progress_bar);
        tvNewDevicesTitle = findViewById(R.id.tv_new_devices_title);

        ListView lvPairedDevices = findViewById(R.id.lv_paired_devices);
        ListView lvNewDevices = findViewById(R.id.lv_new_devices);

        TextView tvNoPairedDevices = findViewById(R.id.tv_no_paired_devices);
        View cardNewDevices = findViewById(R.id.card_new_devices);

        pairedDevicesList = new ArrayList<>();
        newDevicesList = new ArrayList<>();

        pairedDevicesAdapter = new DeviceListAdapter(this, pairedDevicesList);
        newDevicesAdapter = new DeviceListAdapter(this, newDevicesList);

        lvPairedDevices.setAdapter(pairedDevicesAdapter);
        lvNewDevices.setAdapter(newDevicesAdapter);

        // 设置点击事件
        AdapterView.OnItemClickListener deviceClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 取消发现
                bluetoothAdapter.cancelDiscovery();

                // 获取设备
                BluetoothDevice device = (BluetoothDevice) parent.getItemAtPosition(position);

                // 检查设备类型
                int deviceType = device.getType();
                Log.d("DeviceListActivity", "设备类型: " + deviceType +
                        " (1=经典, 2=BLE, 3=双模)");

                // 显示设备类型
                String typeStr = "";
                switch (deviceType) {
                    case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                        typeStr = "经典蓝牙";
                        break;
                    case BluetoothDevice.DEVICE_TYPE_LE:
                        typeStr = "低功耗蓝牙(BLE)";
                        break;
                    case BluetoothDevice.DEVICE_TYPE_DUAL:
                        typeStr = "双模蓝牙";
                        break;
                    default:
                        typeStr = "未知类型";
                        break;
                }

                // 询问用户选择连接方式
                if (deviceType == BluetoothDevice.DEVICE_TYPE_LE ||
                        deviceType == BluetoothDevice.DEVICE_TYPE_DUAL) {

                    new AlertDialog.Builder(DeviceListActivity.this)
                            .setTitle("选择连接方式")
                            .setMessage("设备 " + device.getName() + " 是" + typeStr + "设备")
                            .setPositiveButton("BLE连接", (dialog, which) -> {
                                connectBLEDevice(device);
                            })
                            .setNegativeButton("经典蓝牙连接", (dialog, which) -> {
                                connectDevice(device);
                            })
                            .setNeutralButton("取消", null)
                            .show();
                } else {
                    // 经典蓝牙设备，直接使用SPP连接
                    connectDevice(device);
                }
            }
        };

        lvPairedDevices.setOnItemClickListener(deviceClickListener);
        lvNewDevices.setOnItemClickListener(deviceClickListener);

        btnScan.setOnClickListener(v -> {
            if (checkLocationPermission()) {
                doDiscovery();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void connectDevice(BluetoothDevice device) {
        // 显示连接进度对话框
        connectingDialog = new ProgressDialog(this);
        connectingDialog.setMessage("正在连接 " + device.getName() + "...\n" + device.getAddress());
        connectingDialog.setCancelable(true);
        connectingDialog.setOnCancelListener(dialog -> {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        });
        connectingDialog.show();

        // 添加短暂延迟，确保蓝牙栈准备就绪
        new Handler().postDelayed(() -> {
            // 开始连接
            connectThread = new ConnectThread(device);
            connectThread.start();
        }, 500); // 500ms延迟
    }

    // 连接线程
    private class ConnectThread extends Thread {
        private final BluetoothDevice device;
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
        }

        public void run() {
            Log.i("DeviceListActivity", "BEGIN mConnectThread");
            setName("ConnectThread");

            // 总是取消发现，因为它会减慢连接速度
            bluetoothAdapter.cancelDiscovery();

            // 尝试多种连接方法
            boolean connected = false;

            // 方法1：标准UUID连接
            if (!connected) {
                try {
                    Log.d("DeviceListActivity", "尝试标准UUID连接...");
                    BluetoothSocket tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                    tmp.connect();
                    mmSocket = tmp;
                    connected = true;
                    Log.d("DeviceListActivity", "标准UUID连接成功");
                } catch (IOException e) {
                    Log.e("DeviceListActivity", "标准UUID连接失败: " + e.getMessage());
                    if (mmSocket != null) {
                        try {
                            mmSocket.close();
                        } catch (IOException e2) {
                            Log.e("DeviceListActivity", "关闭socket失败");
                        }
                    }
                }
            }

            // 方法2：使用反射方法，端口1
            if (!connected) {
                try {
                    Log.d("DeviceListActivity", "尝试反射方法连接(端口1)...");
                    BluetoothSocket tmp = (BluetoothSocket) device.getClass()
                            .getMethod("createRfcommSocket", new Class[] {int.class})
                            .invoke(device, 1);
                    tmp.connect();
                    mmSocket = tmp;
                    connected = true;
                    Log.d("DeviceListActivity", "反射方法连接成功(端口1)");
                } catch (Exception e) {
                    Log.e("DeviceListActivity", "反射方法连接失败(端口1): " + e.getMessage());
                    if (mmSocket != null) {
                        try {
                            mmSocket.close();
                        } catch (IOException e2) {
                            Log.e("DeviceListActivity", "关闭socket失败");
                        }
                    }
                }
            }

            // 方法3：使用createInsecureRfcommSocketToServiceRecord
            if (!connected) {
                try {
                    Log.d("DeviceListActivity", "尝试不安全连接...");
                    BluetoothSocket tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                    tmp.connect();
                    mmSocket = tmp;
                    connected = true;
                    Log.d("DeviceListActivity", "不安全连接成功");
                } catch (IOException e) {
                    Log.e("DeviceListActivity", "不安全连接失败: " + e.getMessage());
                    if (mmSocket != null) {
                        try {
                            mmSocket.close();
                        } catch (IOException e2) {
                            Log.e("DeviceListActivity", "关闭socket失败");
                        }
                    }
                }
            }

            // 方法4：尝试不同的端口
            if (!connected) {
                for (int port = 1; port <= 3; port++) {
                    try {
                        Log.d("DeviceListActivity", "尝试反射方法连接(端口" + port + ")...");
                        BluetoothSocket tmp = (BluetoothSocket) device.getClass()
                                .getMethod("createRfcommSocket", new Class[] {int.class})
                                .invoke(device, port);
                        tmp.connect();
                        mmSocket = tmp;
                        connected = true;
                        Log.d("DeviceListActivity", "反射方法连接成功(端口" + port + ")");
                        break;
                    } catch (Exception e) {
                        Log.e("DeviceListActivity", "反射方法连接失败(端口" + port + "): " + e.getMessage());
                        if (mmSocket != null) {
                            try {
                                mmSocket.close();
                            } catch (IOException e2) {
                                Log.e("DeviceListActivity", "关闭socket失败");
                            }
                        }
                    }
                }
            }

            if (connected) {
                // 保存socket
                bluetoothSocket = mmSocket;

                // 连接成功
                Message msg = handler.obtainMessage(1, device);
                handler.sendMessage(msg);
            } else {
                // 所有方法都失败了
                Message msg = handler.obtainMessage(2, "无法连接到设备，请确保设备已开启并在范围内");
                handler.sendMessage(msg);
            }
        }

        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                Log.e("DeviceListActivity", "close() of connect socket failed", e);
            }
        }
    }

    private void setupDeviceLists() {
        // 获取已配对的设备
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        TextView tvNoPairedDevices = findViewById(R.id.tv_no_paired_devices);

        if (pairedDevices.size() > 0) {
            pairedDevicesList.clear();
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesList.add(device);
            }
            pairedDevicesAdapter.notifyDataSetChanged();
            tvNoPairedDevices.setVisibility(View.GONE);
        } else {
            tvNoPairedDevices.setVisibility(View.VISIBLE);
        }
    }

    private boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSION_LOCATION);
                return false;
            }
        }
        return true;
    }

    private void doDiscovery() {
        if (D) Log.d(TAG, "doDiscovery()");
        // 设置标志位为 true，表示这是用户主动发起的扫描
        isUserInitiatedScan = true;
        // 清空新设备列表
        newDevicesList.clear();
        newDevicesAdapter.notifyDataSetChanged();
        tvNewDevicesTitle.setVisibility(View.GONE);
        findViewById(R.id.card_new_devices).setVisibility(View.GONE);

        // 如果正在扫描，先取消
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // 开始经典蓝牙扫描
        bluetoothAdapter.startDiscovery();

        // 同时开始BLE扫描
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startBLEScan();
        }
    }

    // BLE扫描
    private void startBLEScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final android.bluetooth.le.BluetoothLeScanner bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bleScanner != null) {
                // BLE扫描回调
                android.bluetooth.le.ScanCallback bleScanCallback = new android.bluetooth.le.ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                        super.onScanResult(callbackType, result);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            BluetoothDevice device = result.getDevice();

                            // 打印设备信息帮助调试
                            String deviceInfo = "设备: " + (device.getName() != null ? device.getName() : "未知") +
                                    "\n地址: " + device.getAddress() +
                                    "\n类型: " + getDeviceTypeString(device.getType()) +
                                    "\n类别: " + getDeviceClassString(device);

                            Log.d(TAG, deviceInfo);
                            runOnUiThread(() -> {
                                boolean isInList = false;
                                for (BluetoothDevice d : newDevicesList) {
                                    if (d.getAddress().equals(device.getAddress())) {
                                        isInList = true;
                                        break;
                                    }
                                }

                                if (!isInList) {
                                    newDevicesList.add(device);
                                    newDevicesAdapter.notifyDataSetChanged();
                                    tvNewDevicesTitle.setVisibility(View.VISIBLE);
                                    findViewById(R.id.card_new_devices).setVisibility(View.VISIBLE);

                                    Log.d(TAG, "BLE设备发现: " + device.getName() + " - " + device.getAddress());
                                }
                            });
                        }
                    }
                };

                // 开始BLE扫描
                bleScanner.startScan(bleScanCallback);

                // 10秒后停止BLE扫描
                new Handler().postDelayed(() -> {
                    bleScanner.stopScan(bleScanCallback);
                }, 10000);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                setupDeviceLists();
            } else {
                Toast.makeText(this, "需要开启蓝牙才能使用此功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doDiscovery();
            } else {
                Toast.makeText(this, "需要位置权限才能扫描蓝牙设备", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
        if (connectThread != null) {
            connectThread.cancel();
        }
        unregisterReceiver(receiver);
    }

    // 获取设备类型描述
    private String getDeviceTypeString(int type) {
        switch (type) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return "经典蓝牙";
            case BluetoothDevice.DEVICE_TYPE_LE:
                return "低功耗蓝牙(BLE)";
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return "双模";
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
            default:
                return "未知";
        }
    }

    // 获取设备类别描述
    private String getDeviceClassString(BluetoothDevice device) {
        if (device.getBluetoothClass() != null) {
            int deviceClass = device.getBluetoothClass().getDeviceClass();

            // 主要设备类别
            if ((deviceClass & 0x1F00) == 0x0500) return "外设(键盘/鼠标)";
            if ((deviceClass & 0x1F00) == 0x0400) return "音频/视频";
            if ((deviceClass & 0x1F00) == 0x0600) return "成像设备";
            if ((deviceClass & 0x1F00) == 0x0900) return "可穿戴设备";
            if ((deviceClass & 0x1F00) == 0x0700) return "玩具";
            if ((deviceClass & 0x1F00) == 0x0800) return "健康设备";

            return "其他设备";
        }
        return "未知类别";
    }

    /**
     * 连接BLE设备
     */
    private void connectBLEDevice(BluetoothDevice device) {
        // 显示连接进度对话框
        connectingDialog = new ProgressDialog(this);
        connectingDialog.setMessage("正在通过BLE连接 " + device.getName() + "...\n" + device.getAddress());
        connectingDialog.setCancelable(true);
        connectingDialog.show();

        // 初始化BLE服务
        BLEConnectionService bleService = BLEConnectionService.getInstance();
        bleService.init(this, new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case BLEConnectionService.MESSAGE_STATE_CHANGE:
                        if (msg.arg1 == 2) { // STATE_CONNECTED
                            // BLE连接成功
                            handler.obtainMessage(3, device).sendToTarget();
                        }
                        break;
                    case BLEConnectionService.MESSAGE_TOAST:
                        Toast.makeText(DeviceListActivity.this, (String)msg.obj, Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        });

        // 开始连接
        if (!bleService.connect(device)) {
            if (connectingDialog != null && connectingDialog.isShowing()) {
                connectingDialog.dismiss();
            }
            Toast.makeText(this, "BLE连接失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 自定义适配器
    private static class DeviceListAdapter extends ArrayAdapter<BluetoothDevice> {
        private final LayoutInflater inflater;

        public DeviceListAdapter(Context context, ArrayList<BluetoothDevice> devices) {
            super(context, 0, devices);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.device_list_item, parent, false);
                holder = new ViewHolder();
                holder.deviceName = convertView.findViewById(R.id.tv_device_name);
                holder.deviceAddress = convertView.findViewById(R.id.tv_device_address);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            BluetoothDevice device = getItem(position);
            if (device != null) {
                String deviceName = device.getName();
                if (deviceName == null || deviceName.isEmpty()) {
                    deviceName = "未知设备";
                }
                holder.deviceName.setText(deviceName);
                holder.deviceAddress.setText(device.getAddress());
            }

            return convertView;
        }

        static class ViewHolder {
            TextView deviceName;
            TextView deviceAddress;
        }
    }
}