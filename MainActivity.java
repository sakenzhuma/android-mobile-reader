package kz.ncoc.mobilereader;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import kz.ncoc.mobilereader.databinding.ActivityMainBinding;
import kz.ncoc.mobilereader.ui.Common;
import android_serialport_api.SerialPort;
import android_serialport_api.SerialPortTool;
import android_serialport_api.SerialPortFinder;
import kz.ncoc.mobilereader.ui.ReaderComponents;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private SerialPort serialPort;
    private InputStream inputStream;
    private ReadThread mReadThread;
    private String READER_PORT = "/dev/ttyS2";
    public String BAUD_RATE = "9600";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        try {
            App.hash.put(Constants.KEY_PORT, READER_PORT);
            App.hash.put(Constants.KEY_RATE, BAUD_RATE);
            writeFile("1");
            openSerialPort();
        }catch(Exception e){
            App.alert(this, false, e.getMessage());
        }

        binding.btnCalculate.setOnClickListener(v->{});
    }

    public void onDataReceived(final byte[] bArr, final int i) {
        /* This is a place where you get bytes from reader and further
        need to decode and extract facility code and card id from those bytes.
        Hex Result from Reader: A523000008490002734EF8B5
        Binary Result from Reader: 1111111111111111111111111010010110001100100010010010101110011100111011111111111111111111111111111000111111111111111111111111101101010000000000000000000000000000000000000000000000000000
        Card: Proximity 35 bit HID Corporate 1000 format card, 125Mhz
        Expected outcome: Facility Code: 2121 Card ID: 460590
        Some calc online: https://www.brivo.com/card-calculator/
        Additional info: http://www.pagemac.com/projects/rfid/hid_data_formats
        * */
        runOnUiThread(() -> {
            binding.edData.setText(Common.Bytes2HexString(bArr, i));
            StringBuilder sb = new StringBuilder();
            for (byte b : bArr) sb.append(Integer.toBinaryString(b));
            binding.edBitPattern.setText(sb.toString());
            File path = getApplicationContext().getExternalFilesDir(null);
            File file = new File(path, "binary.txt");
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(sb.toString().getBytes());
            stream.close();
        });
    }

    private class ReadThread extends Thread {
        private ReadThread() {}
        ReadThread(MainActivity mainActivity, ReadThread readThread) {
            this();
        }

        public void run() {
            super.run();
            while (!isInterrupted()) {
                try {
                    byte[] bArr = new byte[1024];
                    if (inputStream != null) {
                        int read = inputStream.read(bArr);
                        if (read > 0) {
                            onDataReceived(bArr, read);
                        }
                    } else {
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reader, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            switch (item.getItemId()) {
                case R.id.action_device:
                    ReaderComponents.dlgDeviceDetails(this, "Device Details");
                    break;
                case R.id.action_ports: portOptions(); break;
                case R.id.action_baud_rates: baudRateOptions(); break;
                case R.id.action_bit_length: bitLengthOptions(); break;
                case R.id.action_current_port: currentPort(); break;
                default: break;
            }
        }catch (Exception e) {
            App.alert(this, false, e.getMessage());
        }
        return super.onOptionsItemSelected(item);
    }

    private void portOptions() {
        SerialPortFinder serialPortFinder = new SerialPortFinder();
        String[] devicesPath = serialPortFinder.getAllDevicesPath();
        ReaderComponents.dlgShowList(this, "Ports", devicesPath, (s)->{
            App.hash.put(Constants.KEY_PORT, s);
            restartReader();
        });
    }

    private void baudRateOptions(){
         String[] options = {"1200","2400","4800","9600", "14400", "19200","28800",
        "38400", "57600", "115200"};
        ReaderComponents.dlgShowList(this, "Baud Rates", options, (s)->{
                App.hash.put(Constants.KEY_RATE, s);
                restartReader();
        });
    }

    private void bitLengthOptions(){
        String[] options = {"26","33", "34","35","36","37","40"};
        ReaderComponents.dlgShowList(this, "Bit Lengths", options, (s)->{
            App.hash.put("bit", s);
        });
    }

    private void currentPort(){
        String[] options = {
                "Port: " + App.hash.get(Constants.KEY_PORT),
                "Rate: " + App.hash.get(Constants.KEY_RATE)
        };
        ReaderComponents.dlgShowList(this, "Current Port", options, (s)->{});
    }

    private void restartReader(){
        closeSerialPort();
        openSerialPort();
    }

    private void openSerialPort(){
        try {
            SerialPortTool serialPortTool = new SerialPortTool();
            serialPort = serialPortTool.getSerialPort(
                App.hash.get(Constants.KEY_PORT),
                Integer.parseInt(App.hash.get(Constants.KEY_RATE))
            );
            inputStream = serialPort.getInputStream();
            mReadThread = new ReadThread(this, null);
            mReadThread.start();
        }catch (Exception e){
            App.alert(this, false, e.getMessage());
        }
    }

    private void closeSerialPort(){
        try {
            if (mReadThread != null) mReadThread.interrupt();
            if (inputStream != null) inputStream.close();
            if (serialPort != null) serialPort.close();
        }catch (Exception e){
            App.alert(this, false, e.getMessage());
        }
    }

    public void writeFile(String num) {
        // Powering on = 1 / off = 0 Android U9000 HID Reader from http://m.blovedream.net
        try {
            File fn = new File("/proc/gpiocontrol/set_uhf");
            if(!fn.exists()){
                App.alert(this, false, "File does not exist to power on.");
            }else {
                FileWriter fw = new FileWriter(fn);
                fw.write(num);
                fw.close();
            }
        }catch (Exception e){
            App.alert(this, false, e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            closeSerialPort();
            writeFile("0");
            App.hash.clear();
            binding = null;
        }catch(Exception ignored){}
    }
}
