package com.example.acupunctureinstrument;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * BLE连接服务类
 * 用于管理低功耗蓝牙设备的连接
 */
public class BLEConnectionService {
    private static final String TAG = "BLEConnectionService";

    // 单例
    private static BLEConnectionService instance;

    // 标准的BLE服务和特征UUID
    // 这些是常见的BLE串口服务UUID，您可能需要根据设备修改
//    private static final UUID SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB");
//    private static final UUID CHARACTERISTIC_UUID_TX = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");
//    private static final UUID CHARACTERISTIC_UUID_RX = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");
    private static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHARACTERISTIC_UUID_TX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHARACTERISTIC_UUID_RX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // 通知描述符
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context context;
    private Handler handler;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattCharacteristic rxCharacteristic;

    private int connectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // 消息类型
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    private BLEConnectionService() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static synchronized BLEConnectionService getInstance() {
        if (instance == null) {
            instance = new BLEConnectionService();
        }
        return instance;
    }

    public void init(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
    }

    /**
     * 连接BLE设备
     */
    public boolean connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // 直接连接到远程设备
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        connectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * 连接BLE设备
     */
    public boolean connect(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "Device is null. Unable to connect.");
            return false;
        }

        // 如果已经有连接，先断开
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        Log.d(TAG, "Connecting to device: " + device.getAddress());
        connectionState = STATE_CONNECTING;

        // 发送状态改变消息
        if (handler != null) {
            handler.obtainMessage(MESSAGE_STATE_CHANGE, STATE_CONNECTING, -1).sendToTarget();
        }

        // 连接到GATT服务器
        bluetoothGatt = device.connectGatt(context, false, gattCallback);

        return true;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.disconnect();
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    /**
     * 写数据到BLE设备
     */
    public boolean writeCharacteristic(byte[] value) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        if (txCharacteristic == null) {
            Log.w(TAG, "TX characteristic not found");
            return false;
        }

        txCharacteristic.setValue(value);
        return bluetoothGatt.writeCharacteristic(txCharacteristic);
    }

    /**
     * GATT回调
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server.");

                if (handler != null) {
                    handler.obtainMessage(MESSAGE_STATE_CHANGE, STATE_CONNECTED, -1).sendToTarget();
                }

                // 连接成功后，发现服务
                Log.i(TAG, "Attempting to start service discovery:" +
                        bluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");

                if (handler != null) {
                    handler.obtainMessage(MESSAGE_STATE_CHANGE, STATE_DISCONNECTED, -1).sendToTarget();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: " + status);

                // 获取所有服务
                List<BluetoothGattService> services = gatt.getServices();
                Log.d(TAG, "发现 " + services.size() + " 个服务");

                // 打印所有服务和特征的UUID，帮助调试
                for (BluetoothGattService service : services) {
                    Log.d(TAG, "Service UUID: " + service.getUuid());

                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        Log.d(TAG, "  Characteristic UUID: " + characteristic.getUuid());
                    }
                }

                // 查找我们需要的服务
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    // 获取特征
                    txCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_TX);
                    rxCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_RX);

                    // 设置通知
                    if (rxCharacteristic != null) {
                        setCharacteristicNotification(rxCharacteristic, true);
                    }
                } else {
                    Log.e(TAG, "Service not found: " + SERVICE_UUID);

                    if (handler != null) {
                        Message msg = handler.obtainMessage(MESSAGE_TOAST);
                        msg.obj = "设备不支持所需的服务";
                        handler.sendMessage(msg);
                    }
                }

            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 处理读取的数据
                if (handler != null) {
                    byte[] data = characteristic.getValue();
                    handler.obtainMessage(MESSAGE_READ, data.length, -1, data).sendToTarget();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // 处理通知的数据
            if (handler != null) {
                byte[] data = characteristic.getValue();
                handler.obtainMessage(MESSAGE_READ, data.length, -1, data).sendToTarget();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful");
                if (handler != null) {
                    handler.obtainMessage(MESSAGE_WRITE, -1, -1).sendToTarget();
                }
            } else {
                Log.e(TAG, "Characteristic write failed, status: " + status);
            }
        }
    };

    /**
     * 启用或禁用特征的通知
     */
    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                               boolean enabled) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // 这是特定于心率测量的配置
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public int getConnectionState() {
        return connectionState;
    }

    public boolean isConnected() {
        return connectionState == STATE_CONNECTED;
    }
}