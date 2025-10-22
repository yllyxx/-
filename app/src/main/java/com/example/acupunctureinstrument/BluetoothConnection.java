package com.example.acupunctureinstrument;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 蓝牙连接服务类
 * 管理蓝牙连接的建立、数据收发等
 */
public class BluetoothConnection {
    private static final String TAG = "BluetoothConnection";

    // 标准SPP UUID
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // 单例实例
    private static BluetoothConnection instance;

    // 蓝牙相关
    private BluetoothAdapter bluetoothAdapter;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private BluetoothSocket bluetoothSocket;

    // 状态
    private int state;
    public static final int STATE_NONE = 0;       // 未连接
    public static final int STATE_CONNECTING = 1; // 正在连接
    public static final int STATE_CONNECTED = 2;  // 已连接

    // Handler用于与UI通信
    private Handler handler;

    // 消息类型
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    private BluetoothConnection() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
    }

    public static synchronized BluetoothConnection getInstance() {
        if (instance == null) {
            instance = new BluetoothConnection();
        }
        return instance;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    /**
     * 设置当前状态
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + this.state + " -> " + state);
        this.state = state;

        // 通知UI状态改变
        if (handler != null) {
            handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        }
    }

    /**
     * 获取当前连接状态
     */
    public synchronized int getState() {
        return state;
    }

    /**
     * 开始连接远程设备
     */
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // 取消任何正在进行的连接尝试
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // 取消任何现有的连接
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // 开始连接线程
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * 开始已连接线程
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected");

        // 取消完成连接的线程
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // 取消任何现有的连接线程
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // 启动线程来管理连接并执行传输
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(STATE_CONNECTED);
    }

    /**
     * 停止所有线程
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * 写入数据
     */
    public void write(byte[] out) {
        ConnectedThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED) return;
            r = connectedThread;
        }
        r.write(out);
    }

    /**
     * 连接失败时调用
     */
    private void connectionFailed() {
        setState(STATE_NONE);

        // 发送失败消息给UI
        if (handler != null) {
            Message msg = handler.obtainMessage(MESSAGE_TOAST);
            msg.obj = "连接失败";
            handler.sendMessage(msg);
        }
    }

    /**
     * 连接丢失时调用
     */
    private void connectionLost() {
        setState(STATE_NONE);

        // 发送失败消息给UI
        if (handler != null) {
            Message msg = handler.obtainMessage(MESSAGE_TOAST);
            msg.obj = "连接已断开";
            handler.sendMessage(msg);
        }
    }

    /**
     * 连接线程
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            bluetoothAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            synchronized (BluetoothConnection.this) {
                connectThread = null;
            }

            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * 已连接线程，负责数据传输
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.read(buffer);

                    if (handler != null) {
                        handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer.clone())
                                .sendToTarget();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                // 添加写入前的连接状态检查
                if (mmSocket == null || !mmSocket.isConnected()) {
                    Log.e(TAG, "Socket未连接，无法写入数据");
                    return;
                }

                if (mmOutStream == null) {
                    Log.e(TAG, "OutputStream为null，无法写入数据");
                    return;
                }

                Log.d(TAG, "开始写入数据: " + new String(buffer) + " (长度: " + buffer.length + ")");

                // 写入数据
                mmOutStream.write(buffer);
                mmOutStream.flush(); // 强制刷新输出流

                Log.d(TAG, "数据写入成功");

                // 通知UI数据已发送
                if (handler != null) {
                    handler.obtainMessage(MESSAGE_WRITE, buffer.length, -1, buffer.clone())
                            .sendToTarget();
                }
            } catch (IOException e) {
                Log.e(TAG, "数据写入异常: " + e.getMessage());
                // 可以考虑通知上层连接已断开
                if (handler != null) {
                    handler.obtainMessage(MESSAGE_TOAST, -1, -1, "发送数据失败: " + e.getMessage())
                            .sendToTarget();
                }
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public boolean isConnected() {
        return state == STATE_CONNECTED;
    }
}