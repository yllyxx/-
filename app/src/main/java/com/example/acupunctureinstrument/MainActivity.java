package com.example.acupunctureinstrument;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.res.ColorStateList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.view.MotionEvent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.media.MediaPlayer;
import android.provider.Settings;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.widget.Button;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_WRITE_STORAGE = 100;
    private Handler channelHandler = new Handler(Looper.getMainLooper());
    private Runnable[] channelRunnables = new Runnable[12];
    private static final int SEND_DELAY = 100; // 100ms防抖动延迟

    private Runnable frequencyRunnable = null;  // 频率调节的防抖动任务
    private static final int FREQUENCY_SEND_DELAY = 100; // 频率调节防抖动延迟

    // 🆕 新的ActivityResultLauncher替代startActivityForResult
    private ActivityResultLauncher<Intent> savePatientLauncher;
    private ActivityResultLauncher<Intent> loadPatientLauncher;
    private ActivityResultLauncher<Intent> connectDeviceLauncher;
    private static final int REQUEST_IMPORT_FILE = 101;
    private ActivityResultLauncher<Intent> importFileLauncher;
    private Button btnImport; // 导入按钮
    // UI 控件
    private TextView tvConnectionStatus;
    private TextView tvDeviceId;
    private TextView tvTimer;
    private TextView tvFrequencyValue;
    private Button btnConnect;
    private Button btnback;
    private Button btnSave;
    private Button btnLoad;
    private Button btnExport;  // 改为导出按钮
    private Button btnMinus;
    private Button btnPlus;
    private Button btnStart;
    private Button btnContinuousWave;
    private Button btnIntermittentWave;
    private Button btnSparseWave;
    private SeekBar seekbarFrequency;
    private WaveformView waveformView;
    //    private SeekBar[] channelSeekBars;
    private TextView[] channelValueTextViews;
    private Button[] channelMinusButtons;
    private Button[] channelPlusButtons;
    private int[] channelIntensities; // 存储每个通道的强度值
    private Button btnFreqMinus;
    private Button btnFreqPlus;
    // 状态变量
    private boolean isConnected = false;
    private boolean isRunning = false;
    private int timerMinutes = 30;
    private int currentWaveform = 0; // 0: 连续波, 1: 断续波, 2: 疏密波
    private CountDownTimer countDownTimer;
    private long remainingTime = 30 * 60 * 1000; // 30分钟转毫秒
    private boolean isInitialWaveformSelection = true; // 标记是否是初始波形选择

    // 蓝牙相关
    private BluetoothAdapter bluetoothAdapter;
    private String connectedDeviceAddress;
    private String connectedDeviceName;
    private String connectionType = "SPP"; // 连接类型：SPP或BLE

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🆕 初始化ActivityResultLauncher
        initActivityResultLaunchers();

        initViews();
        setupListeners();
        updateUI();

        // 初始化蓝牙
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_LONG).show();
            btnConnect.setEnabled(false);
        }

        // 设置Handler
        BluetoothConnection.getInstance().setHandler(handler);
    }

    // 🆕 初始化ActivityResultLauncher
    private void initActivityResultLaunchers() {
        // 蓝牙连接Launcher
        connectDeviceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d("CONNECT_RESULT", "蓝牙连接结果: " + result.getResultCode());
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            // 获取选中的设备信息
                            connectedDeviceAddress = data.getStringExtra("device_address");
                            connectedDeviceName = data.getStringExtra("device_name");
                            String connectionType = data.getStringExtra("connection_type");

                            if ("SPP".equals(connectionType)) {
                                // 重新初始化蓝牙服务
                                BluetoothConnection.getInstance().stop();
                                BluetoothConnection.getInstance().setHandler(handler);

                                // 获取设备并重新连接
                                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(connectedDeviceAddress);
                                BluetoothConnection.getInstance().connect(device);
                            } else if ("BLE".equals(connectionType)) {
                                // BLE连接成功后，后续的波形选择都应该显示提示
                                isInitialWaveformSelection = false;
                            }
                        }
                    }
                }
        );
        // 导入文件Launcher
        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            importFromCSV(fileUri);
                        }
                    }
                }
        );
        // 保存患者数据Launcher
        savePatientLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d("SAVE_RESULT", "保存结果: " + result.getResultCode());
                    if (result.getResultCode() == RESULT_OK) {
//                        Toast.makeText(this, "患者数据已保存", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 🎯 加载患者数据Launcher - 这是关键部分
        loadPatientLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d("LOAD_RESULT", "=== 加载患者数据结果 ===");
                    Log.d("LOAD_RESULT", "结果码: " + result.getResultCode());

                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            int frequency = data.getIntExtra("frequency", 50);
                            int[] channelIntensities = data.getIntArrayExtra("channel_intensities");
                            String patientName = data.getStringExtra("patient_name");
                            String patientId = data.getStringExtra("patient_id");

                            Log.d("LOAD_RESULT", "患者姓名: " + patientName);
                            Log.d("LOAD_RESULT", "患者ID: " + patientId);
                            Log.d("LOAD_RESULT", "频率: " + frequency + "Hz");

                            if (channelIntensities != null) {
                                Log.d("LOAD_RESULT", "通道强度数组长度: " + channelIntensities.length);
                                for (int i = 0; i < channelIntensities.length; i++) {
                                    Log.d("LOAD_RESULT", "通道" + (i+1) + ": " + channelIntensities[i]);
                                }
                            } else {
                                Log.e("LOAD_RESULT", "通道强度数组为null！");
                            }

                            //应用加载的参数
                            Log.d("LOAD_RESULT", "准备调用applyLoadedParameters");
                            applyLoadedParameters(frequency, channelIntensities);

//                            Toast.makeText(this, "已加载患者 " + patientName + " 的参数", Toast.LENGTH_LONG).show();
                        } else {
                            Log.e("LOAD_RESULT", "返回数据为null");
                        }
                    } else {
                        Log.d("LOAD_RESULT", "结果码不是RESULT_OK");
                    }
                }
        );
    }

    // 3. 防抖动发送机制
    private void debounceChannelCommand(int channelIndex, int intensity) {
        // 取消之前的延迟任务
        if (channelRunnables[channelIndex] != null) {
            channelHandler.removeCallbacks(channelRunnables[channelIndex]);
        }

        // 创建新的延迟任务
        channelRunnables[channelIndex] = new Runnable() {
            @Override
            public void run() {
                sendChannelIntensityCommand(channelIndex, intensity);
            }
        };

        // 延迟执行
        channelHandler.postDelayed(channelRunnables[channelIndex], SEND_DELAY);
    }

    // 4. 立即发送（用于拖拽结束）
    private void sendChannelIntensityCommandImmediate(int channel, int intensity) {
        // 取消防抖动，立即发送
        if (channelRunnables[channel] != null) {
            channelHandler.removeCallbacks(channelRunnables[channel]);
        }
        sendChannelIntensityCommand(channel, intensity);
    }

    // Handler处理蓝牙消息
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothConnection.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothConnection.STATE_CONNECTED:
                            isConnected = true;
                            // 连接成功后，后续的波形选择都应该显示提示
                            isInitialWaveformSelection = false;
                            updateUI();
                            break;
                        case BluetoothConnection.STATE_CONNECTING:
                            tvConnectionStatus.setText("连接中...");
                            tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                            break;
                        case BluetoothConnection.STATE_NONE:
                            isConnected = false;
                            resetAllParametersOnDisconnect();
                            updateUI();
                            break;
                    }
                    break;
                case BluetoothConnection.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    handleReceivedData(readBuf, msg.arg1);
                    break;
                case BluetoothConnection.MESSAGE_WRITE:
                    // 数据已发送
                    break;
                case BluetoothConnection.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, (String)msg.obj, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void initViews() {
        // 初始化所有控件
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvDeviceId = findViewById(R.id.tv_device_id);
        tvTimer = findViewById(R.id.tv_timer);
        tvFrequencyValue = findViewById(R.id.tv_frequency_value);
        btnback = findViewById(R.id.btn_back);
        btnConnect = findViewById(R.id.btn_connect);
        btnSave = findViewById(R.id.btn_save);
        btnLoad = findViewById(R.id.btn_load);
        btnImport = findViewById(R.id.btn_import);
        btnExport = findViewById(R.id.btn_export); // 修改为导出按钮
        btnMinus = findViewById(R.id.btn_minus);
        btnPlus = findViewById(R.id.btn_plus);
        btnStart = findViewById(R.id.btn_start);

        btnContinuousWave = findViewById(R.id.btn_continuous_wave);
        btnIntermittentWave = findViewById(R.id.btn_intermittent_wave);
        btnSparseWave = findViewById(R.id.btn_sparse_wave);

        seekbarFrequency = findViewById(R.id.seekbar_frequency);
        btnFreqMinus = findViewById(R.id.btn_freq_minus);
        btnFreqPlus = findViewById(R.id.btn_freq_plus);
        // 初始化波形视图
        LinearLayout waveformContainer = findViewById(R.id.waveform_container);
        waveformView = new WaveformView(this);
        waveformView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        waveformContainer.addView(waveformView);

        channelValueTextViews = new TextView[12];
        channelMinusButtons = new Button[12];
        channelPlusButtons = new Button[12];
        channelIntensities = new int[12];

        // 初始化每个通道的控件
        channelValueTextViews[0] = findViewById(R.id.tv_ch1_value);
        channelMinusButtons[0] = findViewById(R.id.btn_ch1_minus);
        channelPlusButtons[0] = findViewById(R.id.btn_ch1_plus);

        channelValueTextViews[1] = findViewById(R.id.tv_ch2_value);
        channelMinusButtons[1] = findViewById(R.id.btn_ch2_minus);
        channelPlusButtons[1] = findViewById(R.id.btn_ch2_plus);

        // ... 继续初始化通道3-12 ...
        channelValueTextViews[2] = findViewById(R.id.tv_ch3_value);
        channelMinusButtons[2] = findViewById(R.id.btn_ch3_minus);
        channelPlusButtons[2] = findViewById(R.id.btn_ch3_plus);

        channelValueTextViews[3] = findViewById(R.id.tv_ch4_value);
        channelMinusButtons[3] = findViewById(R.id.btn_ch4_minus);
        channelPlusButtons[3] = findViewById(R.id.btn_ch4_plus);

        channelValueTextViews[4] = findViewById(R.id.tv_ch5_value);
        channelMinusButtons[4] = findViewById(R.id.btn_ch5_minus);
        channelPlusButtons[4] = findViewById(R.id.btn_ch5_plus);

        channelValueTextViews[5] = findViewById(R.id.tv_ch6_value);
        channelMinusButtons[5] = findViewById(R.id.btn_ch6_minus);
        channelPlusButtons[5] = findViewById(R.id.btn_ch6_plus);

        channelValueTextViews[6] = findViewById(R.id.tv_ch7_value);
        channelMinusButtons[6] = findViewById(R.id.btn_ch7_minus);
        channelPlusButtons[6] = findViewById(R.id.btn_ch7_plus);

        channelValueTextViews[7] = findViewById(R.id.tv_ch8_value);
        channelMinusButtons[7] = findViewById(R.id.btn_ch8_minus);
        channelPlusButtons[7] = findViewById(R.id.btn_ch8_plus);

        channelValueTextViews[8] = findViewById(R.id.tv_ch9_value);
        channelMinusButtons[8] = findViewById(R.id.btn_ch9_minus);
        channelPlusButtons[8] = findViewById(R.id.btn_ch9_plus);

        channelValueTextViews[9] = findViewById(R.id.tv_ch10_value);
        channelMinusButtons[9] = findViewById(R.id.btn_ch10_minus);
        channelPlusButtons[9] = findViewById(R.id.btn_ch10_plus);

        channelValueTextViews[10] = findViewById(R.id.tv_ch11_value);
        channelMinusButtons[10] = findViewById(R.id.btn_ch11_minus);
        channelPlusButtons[10] = findViewById(R.id.btn_ch11_plus);

        channelValueTextViews[11] = findViewById(R.id.tv_ch12_value);
        channelMinusButtons[11] = findViewById(R.id.btn_ch12_minus);
        channelPlusButtons[11] = findViewById(R.id.btn_ch12_plus);

        // 初始化所有通道的强度值为50
        for (int i = 0; i < 12; i++) {
            channelIntensities[i] = 0;
            channelValueTextViews[i].setText(String.valueOf(channelIntensities[i]));
        }
    }

    private Runnable repeatFrequencyRunnable;

    private void startRepeatingFrequencyAction(boolean isIncrement) {
        repeatFrequencyRunnable = new Runnable() {
            @Override
            public void run() {
                int currentProgress = seekbarFrequency.getProgress();

                if (isIncrement && currentProgress < 99) {
                    seekbarFrequency.setProgress(currentProgress + 1);
                    int frequency = currentProgress + 2;
                    tvFrequencyValue.setText(frequency + "Hz");
                    debounceFrequencyCommand(frequency);
                } else if (!isIncrement && currentProgress > 0) {
                    seekbarFrequency.setProgress(currentProgress - 1);
                    int frequency = currentProgress;
                    tvFrequencyValue.setText(frequency + "Hz");
                    debounceFrequencyCommand(frequency);
                }

                // 继续重复
                repeatHandler.postDelayed(this, 100); // 每100ms执行一次
            }
        };

        // 立即执行一次
        repeatHandler.post(repeatFrequencyRunnable);
    }

    private void stopRepeatingFrequencyAction() {
        if (repeatFrequencyRunnable != null) {
            repeatHandler.removeCallbacks(repeatFrequencyRunnable);
        }
    }
    //选择csv文件
    private void selectCSVFileToImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*"); // CSV文件
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/comma-separated-values"});

        try {
            importFileLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }
    // CSV行解析
    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 两个连续的引号表示一个引号字符
                    currentField.append('"');
                    i++; // 跳过下一个引号
                } else {
                    // 切换引号状态
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // 字段分隔符
                result.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        // 添加最后一个字段
        result.add(currentField.toString());

        return result.toArray(new String[0]);
    }
    private void importFromCSV(Uri fileUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line;
            int lineNumber = 0;
            int successCount = 0;
            int updateCount = 0;
            int errorCount = 0;

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            PatientDao patientDao = new PatientDao(this);

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // 跳过第一行标题和空行
                if (lineNumber == 1 || line.trim().isEmpty()) {
                    continue;
                }

                try {
                    // 解析CSV行(处理包含逗号的字段)
                    String[] values = parseCSVLine(line);

                    if (values.length < 20) { // 至少要有基本字段 + 12个通道
                        Log.e("IMPORT_CSV", "第" + lineNumber + "行数据不完整");
                        errorCount++;
                        continue;
                    }

                    // 提取数据
                    String patientId = values[0].trim();
                    String patientName = values[1].trim();
                    int age = 0;
                    try {
                        age = Integer.parseInt(values[2].trim());
                    } catch (Exception e) {
                        // 年龄解析失败,使用默认值0
                    }

                    String gender = values[3].trim();
                    String treatmentContent = values[4].trim();
                    String remark = values[5].trim();
                    int frequency = Integer.parseInt(values[6].trim());

                    // 解析12个通道强度
                    JSONArray channelArray = new JSONArray();
                    for (int i = 0; i < 12; i++) {
                        int intensity = Integer.parseInt(values[7 + i].trim());
                        channelArray.put(intensity);
                    }

                    // 检查患者ID是否存在
                    Patient existingPatient = patientDao.getPatientByPatientId(patientId);

                    if (existingPatient != null) {
                        // 更新现有记录
                        existingPatient.setPatientName(patientName);
                        existingPatient.setAge(age);
                        existingPatient.setGender(gender);
                        existingPatient.setTreatmentContent(treatmentContent);
                        existingPatient.setRemark(remark);
                        existingPatient.setFrequency(frequency);
                        existingPatient.setChannelIntensities(channelArray.toString());
                        existingPatient.setUpdateTime(System.currentTimeMillis());

                        patientDao.updatePatient(existingPatient);
                        updateCount++;
                    } else {
                        // 创建新记录
                        Patient patient = new Patient(patientId, patientName, age, gender,
                                treatmentContent, remark, frequency, channelArray.toString());
                        patientDao.insertPatient(patient);
                        successCount++;
                    }

                } catch (Exception e) {
                    Log.e("IMPORT_CSV", "第" + lineNumber + "行解析失败", e);
                    errorCount++;
                }
            }

            reader.close();
            inputStream.close();

            // 显示导入结果
            String message = "导入完成！\n" +
                    "新增: " + successCount + " 条\n" +
                    "更新: " + updateCount + " 条\n" +
                    "失败: " + errorCount + " 条";

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("导入结果")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show();

            Log.d("IMPORT_CSV", "CSV导入完成: 成功=" + successCount + ", 更新=" + updateCount + ", 失败=" + errorCount);

        } catch (Exception e) {
            Log.e("IMPORT_CSV", "CSV导入失败", e);
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void setupListeners() {
        // 连接按钮 - 使用新的launcher
        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnectDevice();
            } else {
                Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
                connectDeviceLauncher.launch(intent);
            }
        });

        // 保存按钮 - 使用新的launcher
        btnSave.setOnClickListener(v -> saveSettings());

        // 加载按钮 - 使用新的launcher
        btnLoad.setOnClickListener(v -> loadSettings());
        //加载按钮
        btnImport.setOnClickListener(v -> selectCSVFileToImport());
        // 导出按钮 - 修改为导出功能
        btnExport.setOnClickListener(v -> exportDatabase());

        // 定时器控制
        btnMinus.setOnClickListener(v -> decreaseTimer());
        btnPlus.setOnClickListener(v -> increaseTimer());
        btnStart.setOnClickListener(v -> {
            if (isRunning) {
                stopTimer();
            } else {
                startTimer();
            }
        });

        // 波形选择
        btnContinuousWave.setOnClickListener(v -> selectWaveform(0));
        btnIntermittentWave.setOnClickListener(v -> selectWaveform(1));
        btnSparseWave.setOnClickListener(v -> selectWaveform(2));

        // 默认选中连续波
        selectWaveform(0);

        // 频率调节
        seekbarFrequency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if(progress == 0) {
//                    progress = 1;
//                }
//                int frequency = 3 * (progress + 1);
//                if(frequency == 0) {
//                    frequency = 3;
//                }
                int frequency = progress + 1;
                // 立即更新UI显示
                tvFrequencyValue.setText(frequency + "Hz");

                if (fromUser) {
                    debounceFrequencyCommand(frequency);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖拽时的处理
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                int finalFrequency = (progress + 1);
                sendFrequencyCommandImmediate(finalFrequency);
            }
        });

        // 频率减号按钮
        btnFreqMinus.setOnClickListener(v -> {
            int currentProgress = seekbarFrequency.getProgress();
            if (currentProgress > 0) {
                seekbarFrequency.setProgress(currentProgress - 1);
                int frequency = currentProgress;  // 减1后的progress就是frequency-1
                tvFrequencyValue.setText(frequency + "Hz");
                debounceFrequencyCommand(frequency);
            }
        });

// 频率加号按钮
        btnFreqPlus.setOnClickListener(v -> {
            int currentProgress = seekbarFrequency.getProgress();
            if (currentProgress < 99) {
                seekbarFrequency.setProgress(currentProgress + 1);
                int frequency = currentProgress + 2;  // progress+1后，频率是progress+2
                tvFrequencyValue.setText(frequency + "Hz");
                debounceFrequencyCommand(frequency);
            }
        });

        // 频率减号按钮长按事件 - 连续减少
        btnFreqMinus.setOnLongClickListener(v -> {
            startRepeatingFrequencyAction(false);
            return true;
        });

        // 频率加号按钮长按事件 - 连续增加
        btnFreqPlus.setOnLongClickListener(v -> {
            startRepeatingFrequencyAction(true);
            return true;
        });

        // 触摸释放事件 - 停止连续增减
        btnFreqMinus.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopRepeatingFrequencyAction();
            }
            return false;
        });

        btnFreqPlus.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopRepeatingFrequencyAction();
            }
            return false;
        });

        // 设置12路强度控制的监听器
        for (int i = 0; i < 12; i++) {
            final int channelIndex = i;

            // 减号按钮
            channelMinusButtons[i].setOnClickListener(v -> {
                if (channelIntensities[channelIndex] > 0) {
                    channelIntensities[channelIndex]--;
                    updateChannelDisplay(channelIndex);
                    debounceChannelCommand(channelIndex, channelIntensities[channelIndex]);
                }
            });

            // 加号按钮
            channelPlusButtons[i].setOnClickListener(v -> {
                if (channelIntensities[channelIndex] < 100) {
                    channelIntensities[channelIndex]++;
                    updateChannelDisplay(channelIndex);
                    debounceChannelCommand(channelIndex, channelIntensities[channelIndex]);
                }
            });

            // 长按事件 - 连续增减
            channelMinusButtons[i].setOnLongClickListener(v -> {
                startRepeatingAction(channelIndex, false);
                return true;
            });

            channelPlusButtons[i].setOnLongClickListener(v -> {
                startRepeatingAction(channelIndex, true);
                return true;
            });

            // 触摸释放事件 - 停止连续增减
            channelMinusButtons[i].setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP ||
                        event.getAction() == MotionEvent.ACTION_CANCEL) {
                    stopRepeatingAction();
                }
                return false;
            });

            channelPlusButtons[i].setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP ||
                        event.getAction() == MotionEvent.ACTION_CANCEL) {
                    stopRepeatingAction();
                }
                return false;
            });
        }
    }

    // 更新通道显示
    private void updateChannelDisplay(int channelIndex) {
        channelValueTextViews[channelIndex].setText(String.valueOf(channelIntensities[channelIndex]));
    }

    // 用于长按连续增减
    private Handler repeatHandler = new Handler(Looper.getMainLooper());
    private Runnable repeatRunnable;

    private void startRepeatingAction(int channelIndex, boolean isIncrement) {
        repeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isIncrement && channelIntensities[channelIndex] < 100) {
                    channelIntensities[channelIndex]++;
                } else if (!isIncrement && channelIntensities[channelIndex] > 0) {
                    channelIntensities[channelIndex]--;
                }

                updateChannelDisplay(channelIndex);
                debounceChannelCommand(channelIndex, channelIntensities[channelIndex]);

                // 继续重复
                repeatHandler.postDelayed(this, 100); // 每100ms执行一次
            }
        };

        // 立即执行一次
        repeatHandler.post(repeatRunnable);
    }

    private void stopRepeatingAction() {
        if (repeatRunnable != null) {
            repeatHandler.removeCallbacks(repeatRunnable);
        }
    }
    // 频率防抖动方法
    private void debounceFrequencyCommand(int frequency) {
        if (frequencyRunnable != null) {
            channelHandler.removeCallbacks(frequencyRunnable);
        }

        frequencyRunnable = new Runnable() {
            @Override
            public void run() {
                sendFrequencyCommand(frequency);
                Log.d("FrequencyControl", "防抖动发送频率命令: " + frequency + "Hz");
            }
        };

        channelHandler.postDelayed(frequencyRunnable, FREQUENCY_SEND_DELAY);
    }

    // 频率调节立即发送方法
    private void sendFrequencyCommandImmediate(int frequency) {
        if (frequencyRunnable != null) {
            channelHandler.removeCallbacks(frequencyRunnable);
            frequencyRunnable = null;
        }
        sendFrequencyCommand(frequency);
        Log.d("FrequencyControl", "立即发送频率命令: " + frequency + "Hz");
    }


    private void applyLoadedParameters(int frequency, int[] channelIntensities) {
        Log.d("APPLY_PARAMS", "=== 开始应用参数 ===");
        Log.d("APPLY_PARAMS", "目标频率: " + frequency + "Hz");

        try {
            // 频率转换和更新
            int seekBarProgress;
            seekBarProgress = frequency - 1;  // 频率1-100对应progress 0-99
            seekBarProgress = Math.max(0, Math.min(99, seekBarProgress));

            Log.d("APPLY_PARAMS", "计算的滑动块位置: " + seekBarProgress);

            // 更新频率UI
            seekbarFrequency.setProgress(seekBarProgress);
            tvFrequencyValue.setText(frequency + "Hz");

            // 更新通道强度
            if (channelIntensities != null && channelIntensities.length >= 12) {
                Log.d("APPLY_PARAMS", "开始更新通道强度");
                for (int i = 0; i < 12; i++) {
                    // 更新成员变量
                    this.channelIntensities[i] = Math.max(0, Math.min(100, channelIntensities[i]));
                    // 更新显示
                    updateChannelDisplay(i);

                    Log.d("APPLY_PARAMS", "通道" + (i+1) + " 更新到 " + this.channelIntensities[i]);
                }
            } else {
                Log.e("APPLY_PARAMS", "通道强度数组为null或长度不足");
            }

            // 发送参数到蓝牙设备（如果已连接）
            if (isConnected) {
                Log.d("APPLY_PARAMS", "发送参数到蓝牙设备");
                sendFrequencyCommand(frequency);

                // 延迟发送通道强度命令
                new Handler().postDelayed(() -> {
                    if (channelIntensities != null) {
                        for (int i = 0; i < Math.min(12, channelIntensities.length); i++) {
                            sendChannelIntensityCommand(i, this.channelIntensities[i]);
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }, 200);
            } else {
                Log.d("APPLY_PARAMS", "设备未连接，跳过蓝牙命令发送");
            }

            Log.d("APPLY_PARAMS", "=== 参数应用完成 ===");

        } catch (Exception e) {
            Log.e("APPLY_PARAMS", "参数应用失败", e);
            Toast.makeText(this, "参数应用失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettings() {
        Log.d("SAVE_SETTINGS", "=== 保存设置 ===");

        // 获取当前参数
        int progress = seekbarFrequency.getProgress();
        int currentFrequency = progress + 1;
        Log.d("SAVE_SETTINGS", "滑动块位置: " + progress);
        Log.d("SAVE_SETTINGS", "计算的频率: " + currentFrequency + "Hz");

        int[] channelIntensitiesArray = new int[12];
        for (int i = 0; i < 12; i++) {
            channelIntensitiesArray[i] = channelIntensities[i];
        }

        Intent intent = new Intent(MainActivity.this, SavePatientActivity.class);
        intent.putExtra("frequency", currentFrequency);
        intent.putExtra("channel_intensities", channelIntensitiesArray);
        savePatientLauncher.launch(intent);
    }

    private void loadSettings() {
        Log.d("LOAD_SETTINGS", "=== 加载设置 ===");

        // 🆕 使用新的launcher启动患者列表页面
        Intent intent = new Intent(MainActivity.this, PatientListActivity.class);
        loadPatientLauncher.launch(intent);
    }

    // 导出数据库功能
    private void exportDatabase() {
        // 检查写入权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 10以下需要写入权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
                return;
            }
        }

        // 执行导出
        performExportDatabase();
    }

    private void performExportDatabase() {
        try {
            // 获取所有患者数据
            PatientDao patientDao = new PatientDao(this);
            List<Patient> patients = patientDao.getAllPatients();

            if (patients.isEmpty()) {
                Toast.makeText(this, "没有数据可导出", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建导出目录（使用Downloads文件夹）
            File exportDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10及以上使用公共Downloads目录
                exportDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "针疗仪数据");
            } else {
                // Android 10以下也使用Downloads目录
                exportDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "针疗仪数据");
            }

            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            // 生成带时间戳的CSV文件名
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String fileName = "针疗仪患者数据_" + sdf.format(new Date()) + ".csv";
            File csvFile = new File(exportDir, fileName);

            // 创建CSV内容
            StringBuilder csvContent = new StringBuilder();

            // 添加CSV标题行（UTF-8 BOM以支持Excel正确显示中文）
            csvContent.append("\uFEFF"); // UTF-8 BOM
            csvContent.append("患者ID,患者姓名,年龄,性别,治疗内容,备注,频率(Hz),");

            // 添加12个通道的标题
            for (int i = 1; i <= 12; i++) {
                csvContent.append("通道").append(i).append("强度");
                if (i < 12) csvContent.append(",");
            }
            csvContent.append(",创建时间,更新时间\n");

            // 添加数据行
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            for (Patient patient : patients) {
                // 基本信息
                csvContent.append(escapeCSV(patient.getPatientId())).append(",");
                csvContent.append(escapeCSV(patient.getPatientName())).append(",");
                csvContent.append(patient.getAge()).append(",");
                csvContent.append(escapeCSV(patient.getGender())).append(",");
                csvContent.append(escapeCSV(patient.getTreatmentContent())).append(",");
                csvContent.append(escapeCSV(patient.getRemark())).append(",");
                csvContent.append(patient.getFrequency()).append(",");

                // 解析通道强度
                try {
                    JSONArray jsonArray = new JSONArray(patient.getChannelIntensities());
                    for (int i = 0; i < 12; i++) {
                        if (i < jsonArray.length()) {
                            csvContent.append(jsonArray.getInt(i));
                        } else {
                            csvContent.append("0");
                        }
                        if (i < 11) csvContent.append(",");
                    }
                } catch (Exception e) {
                    // 如果解析失败，填充0
                    for (int i = 0; i < 12; i++) {
                        csvContent.append("0");
                        if (i < 11) csvContent.append(",");
                    }
                }

                // 时间信息
                csvContent.append(",");
                csvContent.append(dateFormat.format(new Date(patient.getCreateTime()))).append(",");
                csvContent.append(dateFormat.format(new Date(patient.getUpdateTime()))).append("\n");
            }

            // 写入文件
            FileOutputStream fos = new FileOutputStream(csvFile);
            fos.write(csvContent.toString().getBytes("UTF-8"));
            fos.close();

            // 提示成功
            String message = "数据导出成功！\n" +
                    "导出数量：" + patients.size() + " 条记录\n" +
                    "文件位置：下载/针疗仪数据/\n" +
                    "文件名：" + fileName;

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("导出成功")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show();

            Log.d("EXPORT_CSV", "CSV导出成功: " + csvFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e("EXPORT_CSV", "CSV导出失败", e);
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // CSV转义函数，处理包含逗号、引号或换行的内容
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }

        // 如果包含逗号、引号或换行，需要用引号包围
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // 将引号替换为两个引号
            value = value.replace("\"", "\"\"");
            // 用引号包围
            return "\"" + value + "\"";
        }

        return value;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予后执行导出
                performExportDatabase();
            } else {
                Toast.makeText(this, "需要存储权限才能导出数据库", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void decreaseTimer() {
        if (timerMinutes > 1) {
            timerMinutes--;
            remainingTime = timerMinutes * 60 * 1000;
            updateTimerDisplay();
        }
    }

    private void increaseTimer() {
        if (timerMinutes < 99) {
            timerMinutes++;
            remainingTime = timerMinutes * 60 * 1000;
            updateTimerDisplay();
        }
    }

    private void updateUI() {
        // 更新连接状态
        if (isConnected) {
            tvConnectionStatus.setText("已连接");
            tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            btnConnect.setText("断开连接");

            // 显示设备信息
            if (connectedDeviceName != null && !connectedDeviceName.isEmpty()) {
                tvDeviceId.setText(connectedDeviceName);
            } else if (connectedDeviceAddress != null) {
                tvDeviceId.setText(connectedDeviceAddress);
            }
        } else {
            tvConnectionStatus.setText("未连接");
            tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            btnConnect.setText("蓝牙连接");
            tvDeviceId.setText("666888");
        }

        // 更新定时器显示
        updateTimerDisplay();

        // 更新开始按钮
        btnStart.setText(isRunning ? "停止" : "开始");
        if (isRunning) {
            btnStart.setBackgroundColor(0xFFFFB6C1); // 浅粉色 (Light Pink)
        } else {
            btnStart.setBackgroundColor(0xFF90EE90); // 浅绿色 (Light Green)
        }
        btnStart.setTextColor(0xFF000000); // 黑色字体
    }

    private void updateTimerDisplay() {
        int minutes = (int) (remainingTime / 60000);
        int seconds = (int) ((remainingTime % 60000) / 1000);
        tvTimer.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void disconnectDevice() {
        if ("BLE".equals(connectionType)) {
            BLEConnectionService.getInstance().disconnect();
            BLEConnectionService.getInstance().close();
        } else {
            BluetoothConnection.getInstance().stop();
        }

        isConnected = false;
        connectedDeviceAddress = null;
        connectedDeviceName = null;
        connectionType = "SPP";
        //断开连接重置所有参数
        resetAllParametersOnDisconnect();
        updateUI();
//        Toast.makeText(this, "设备已断开", Toast.LENGTH_SHORT).show();
    }

    private void resetAllParametersOnDisconnect() {
        // 如果正在运行，先停止定时器
        if (isRunning) {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            isRunning = false;
        }

        // 重置频率到初始值（1Hz）
        int initialFrequency = 1;
        seekbarFrequency.setProgress(0); // 频率1Hz对应progress 0
        tvFrequencyValue.setText(initialFrequency + "Hz");

        // 重置所有通道强度到0
        for (int i = 0; i < 12; i++) {
            channelIntensities[i] = 0;
            updateChannelDisplay(i);
        }

        // 重置波形到连续波（默认）
        currentWaveform = 0;
        btnContinuousWave.setBackgroundTintList(ColorStateList.valueOf(0xFFADD8E6));
        btnIntermittentWave.setBackgroundTintList(ColorStateList.valueOf(0xFFF0F0F0));
        btnSparseWave.setBackgroundTintList(ColorStateList.valueOf(0xFFF0F0F0));
        if (waveformView != null) {
            waveformView.setWaveformType(0);
        }

        // 重置定时器到30分钟
        timerMinutes = 30;
        remainingTime = timerMinutes * 60 * 1000;
        updateTimerDisplay();

        // 重置初始波形选择标记
        isInitialWaveformSelection = true;

        // 确保开始按钮显示为"开始"状态
        btnStart.setText("开始");
        btnStart.setBackgroundColor(0xFF90EE90); // 浅绿色
        btnStart.setTextColor(0xFF000000); // 黑色字体

        Log.d("MainActivity", "蓝牙断开，所有参数已重置到初始值");
    }
    private void resetAllParameters() {
        // 重置频率到初始值（1Hz）
        int initialFrequency = 1;
        seekbarFrequency.setProgress(0); // 频率1Hz对应progress 0
        tvFrequencyValue.setText(initialFrequency + "Hz");

        // 重置所有通道强度到0
        for (int i = 0; i < 12; i++) {
            channelIntensities[i] = 0;
            updateChannelDisplay(i);
        }

        // 重置波形到连续波（默认）
        selectWaveform(0);

        // 重置定时器到30分钟
//        timerMinutes = 30;
//        remainingTime = timerMinutes * 60 * 1000;
//        updateTimerDisplay();

        Log.d("MainActivity", "所有参数已重置到初始值");
    }
    private void startTimer() {
        if (!isConnected) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
            return;
        }
        // 重置所有参数到初始值
        resetAllParameters();
        isRunning = true;
        countDownTimer = new CountDownTimer(remainingTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingTime = millisUntilFinished;
                updateTimerDisplay();
            }

            @Override
            public void onFinish() {
                isRunning = false;
                remainingTime = timerMinutes * 60 * 1000;
                updateUI();
                // 播放提示音
                playNotificationSound();
                Toast.makeText(MainActivity.this, "治疗完成", Toast.LENGTH_LONG).show();
                // 发送停止治疗命令
                if ("BLE".equals(connectionType)) {
                    if (BLEConnectionService.getInstance().isConnected()) {
                        BLEConnectionService.getInstance().writeCharacteristic("V\n".getBytes());
                    }
                } else {
                    if (BluetoothConnection.getInstance().isConnected()) {
                        BluetoothConnection.getInstance().write("V\n".getBytes());
                    }
                }
            }
        }.start();

        updateUI();
        // 发送开始治疗命令
        if ("BLE".equals(connectionType)) {
            if (BLEConnectionService.getInstance().isConnected()) {
                BLEConnectionService.getInstance().writeCharacteristic("V\n".getBytes());
            }
        } else {
            if (BluetoothConnection.getInstance().isConnected()) {
                BluetoothConnection.getInstance().write("V\n".getBytes());
            }
        }
        Toast.makeText(this, "开始治疗", Toast.LENGTH_SHORT).show();
    }
    // 播放提示音
    private void playNotificationSound() {
        try {
            // 使用MediaPlayer播放系统默认提示音
            final MediaPlayer mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI);

            if (mediaPlayer != null) {
                // 设置音量（0.0f到1.0f）
                mediaPlayer.setVolume(1.0f, 1.0f);

                // 播放完成后的监听器
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    private int playCount = 0;

                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        playCount++;
                        if (playCount < 3) {
                            // 延迟300ms后再次播放
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mp.start();
                                }
                            }, 300);
                        } else {
                            // 播放完3次后释放资源
                            mp.release();
                        }
                    }
                });

                // 开始播放第一次
                mediaPlayer.start();

                Log.d("MainActivity", "开始播放提示音");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "播放提示音失败: " + e.getMessage());
            // 如果MediaPlayer失败，尝试使用ToneGenerator作为备选
            playBeepSound();
        }
    }
    private void playBeepSound() {
        try {
            // 创建ToneGenerator，音量设为最大
            final ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);

            Handler beepHandler = new Handler(Looper.getMainLooper());

            // 播放3次蜂鸣声
            for (int i = 0; i < 3; i++) {
                final int index = i;
                beepHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // 播放蜂鸣声，持续200ms
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200);

                        // 最后一次播放后释放资源
                        if (index == 2) {
                            beepHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    toneGen.release();
                                }
                            }, 300);
                        }
                    }
                }, i * 500); // 每次间隔500ms
            }

            Log.d("MainActivity", "播放蜂鸣声");
        } catch (Exception e) {
            Log.e("MainActivity", "播放蜂鸣声失败: " + e.getMessage());
        }
    }
    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isRunning = false;
        remainingTime = timerMinutes * 60 * 1000;
        updateUI();
        // 发送停止治疗命令
        if ("BLE".equals(connectionType)) {
            if (BLEConnectionService.getInstance().isConnected()) {
                BLEConnectionService.getInstance().writeCharacteristic("S\n".getBytes());
            }
        } else {
            if (BluetoothConnection.getInstance().isConnected()) {
                BluetoothConnection.getInstance().write("S\n".getBytes());
            }
        }
        Toast.makeText(this, "治疗已停止", Toast.LENGTH_SHORT).show();
    }

    private void selectWaveform(int waveformType) {
        currentWaveform = waveformType;

        // 更新按钮状态
        btnContinuousWave.setBackgroundTintList(ColorStateList.valueOf(
                waveformType == 0 ? 0xFFADD8E6 : 0xFFF0F0F0));
        btnContinuousWave.setTextColor(0xFF000000);

        btnIntermittentWave.setBackgroundTintList(ColorStateList.valueOf(
                waveformType == 1 ? 0xFFADD8E6 : 0xFFF0F0F0));
        btnIntermittentWave.setTextColor(0xFF000000);

        btnSparseWave.setBackgroundTintList(ColorStateList.valueOf(
                waveformType == 2 ? 0xFFADD8E6 : 0xFFF0F0F0));
        btnSparseWave.setTextColor(0xFF000000);

        // 更新波形显示
        if (waveformView != null) {
            waveformView.setWaveformType(waveformType);
        }

        sendWaveformCommand(waveformType);
    }

//    private void sendWaveformCommand(int waveformType) {
//        String[] waveformNames = {"连续波", "断续波", "疏密波"};
////        String command = "";
//        if (isConnected) {
//            String command = "";
//            if (waveformType == 0){
//                command = "L" + "\n";
//            } else if (waveformType == 1) {
//                command = "D" + "\n";
//            }
//            else {
//                command = "A" + "\n";
//            }
////            String command = "W" + waveformType + "\n";
//            if ("BLE".equals(connectionType)) {
//                if (BLEConnectionService.getInstance().isConnected()) {
//                    BLEConnectionService.getInstance().writeCharacteristic(command.getBytes());
//                    Toast.makeText(this, "已发送(BLE)：" + waveformNames[waveformType], Toast.LENGTH_SHORT).show();
//                }
//            } else {
//                if (BluetoothConnection.getInstance().isConnected()) {
//                    BluetoothConnection.getInstance().write(command.getBytes());
//                    Toast.makeText(this, "已发送：" + waveformNames[waveformType], Toast.LENGTH_SHORT).show();
//                }
//            }
//        } else {
//            // 只有在非初始选择时才显示未连接提示
//            if (!isInitialWaveformSelection) {
//                Toast.makeText(this, "设备未连接", Toast.LENGTH_SHORT).show();
//            }
//        }
//
//        // 清除初始选择标记
//        if (isInitialWaveformSelection) {
//            isInitialWaveformSelection = false;
//        }
//    }

private void sendWaveformCommand(int waveformType) {
    String[] waveformNames = {"连续波", "断续波", "疏密波"};
    if (isConnected) {
        char[] commands = {'L', 'D', 'A'};
        String command = commands[waveformType] + "\n";
        byte[] data = command.getBytes(StandardCharsets.UTF_8);  // 明确指定编码

        if ("BLE".equals(connectionType)) {
            if (BLEConnectionService.getInstance().isConnected()) {
                BLEConnectionService.getInstance().writeCharacteristic(data);
                Toast.makeText(this, "已发送(BLE)：" + waveformNames[waveformType], Toast.LENGTH_SHORT).show();
            }
        } else {
            if (BluetoothConnection.getInstance().isConnected()) {
                BluetoothConnection.getInstance().write(data);
                Toast.makeText(this, "已发送：" + waveformNames[waveformType], Toast.LENGTH_SHORT).show();
            }
        }
    } else {
        if (!isInitialWaveformSelection) {
            Toast.makeText(this, "设备未连接", Toast.LENGTH_SHORT).show();
        }
    }

    if (isInitialWaveformSelection) {
        isInitialWaveformSelection = false;
    }
}




    private void sendFrequencyCommand(int frequency) {
        if (isConnected) {
            String command = String.format("F99%03d\n", frequency);
            if ("BLE".equals(connectionType)) {
                if (BLEConnectionService.getInstance().isConnected()) {
                    BLEConnectionService.getInstance().writeCharacteristic(command.getBytes());
                }
            } else {
                if (BluetoothConnection.getInstance().isConnected()) {
                    BluetoothConnection.getInstance().write(command.getBytes());
                }
            }
        }
    }

    private void sendChannelIntensityCommand(int channel, int intensity) {
        if (isConnected) {
            int actualChannel = channel + 1;
            if (intensity == 100) {
                intensity = 99;
            }
            String command = String.format("Q%02d%02dx\n", actualChannel, intensity);

            if ("BLE".equals(connectionType)) {
                if (BLEConnectionService.getInstance().isConnected()) {
                    BLEConnectionService.getInstance().writeCharacteristic(command.getBytes());
                }
            } else {
                if (BluetoothConnection.getInstance().isConnected()) {
                    BluetoothConnection.getInstance().write(command.getBytes());
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (repeatFrequencyRunnable != null) {
            repeatHandler.removeCallbacks(repeatFrequencyRunnable);
        }
        // 清理Handler任务
        if (channelHandler != null) {
            for (Runnable runnable : channelRunnables) {
                if (runnable != null) {
                    channelHandler.removeCallbacks(runnable);
                }
            }

            if (frequencyRunnable != null) {
                channelHandler.removeCallbacks(frequencyRunnable);
                frequencyRunnable = null;
            }
        }

        if (isConnected) {
            disconnectDevice();
        }
    }

    /**
     * 处理接收到的数据
     */
    private void handleReceivedData(byte[] data, int length) {
        String receivedData = new String(data, 0, length);
        Log.d("MainActivity", "接收到数据: " + receivedData);
    }
}