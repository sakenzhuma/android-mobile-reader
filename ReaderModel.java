package kz.ncoc.mobreader.data.models;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.InputStream;
import java.math.BigInteger;
import android_serialport_api.SerialPort;
import android_serialport_api.SerialPortTool;
import at.favre.lib.bytes.Bytes;
import kz.ncoc.mobreader.App;

public class ReaderModel extends ViewModel {
    private SerialPort serialPort;
    private SerialPortTool serialPortTool;
    private InputStream inputStream;

    private final MutableLiveData<String> readerPort;
    private final MutableLiveData<Integer> readerBaudRate;

    private final MutableLiveData<String> data;
    private final MutableLiveData<String> message;
    private final MutableLiveData<Boolean> loopState;
    private final MutableLiveData<Boolean> readState;

    public ReaderModel(){
        data = new MutableLiveData<>();
        message = new MutableLiveData<>();
        loopState = new MutableLiveData<>();
        readState = new MutableLiveData<>();
        readerPort = new MutableLiveData<>();
        readerBaudRate = new MutableLiveData<>();
    }

    public LiveData<String> getData(){ return data; }
    public void setData(String str){ data.setValue(str); }
    public LiveData<String> getMessage(){ return message; }
    public void setMessage(String str){ message.setValue(str); }
    public LiveData<Boolean> getLoopState(){ return loopState; }
    public void setLoopState(boolean b){ loopState.setValue(b); }
    public LiveData<Boolean> getReadState(){ return readState; }
    public void setReadState(boolean b){ readState.setValue(b); }
    public LiveData<String> getReaderPort(){ return readerPort; }
    public void setReaderPort(String b){ readerPort.setValue(b); }
    public LiveData<Integer> getReaderBaudRate(){ return readerBaudRate; }
    public void setReaderBaudRate(int b){ readerBaudRate.setValue(b); }

    public void close(){
        try {
            if(inputStream != null) inputStream.close();
            if(serialPort != null) serialPort.close();
        }catch (Exception e){
            message.setValue("Err: " + e.getMessage());
        }
    }

    public void open(){
        try {
            serialPortTool = new SerialPortTool();
            serialPort = serialPortTool.getSerialPort(readerPort.getValue(), readerBaudRate.getValue());
            inputStream = serialPort.getInputStream();
            App.es.execute(()->{
                while (loopState.getValue()){
                    try {
                        int av = inputStream.available();
                        if(av > 0) {
                            byte[] buffer = new byte[av];
                            int size = inputStream.read(buffer);
                            Bytes b = Bytes.from(buffer);
                            String tmp = b.encodeHex(true);
                            if(size > 0) {
                                App.handler.post(() -> {
                                    data.setValue(tmp);
                                });
                            }
                        } else { Thread.sleep(50); }
                    } catch (Exception e) {
                        App.handler.post(()->{
                            message.setValue(e.getMessage());
                            loopState.setValue(false);
                        });
                    }
                }
            });
        } catch (Exception e) {
            message.setValue("Err: " + e.getMessage());
        }
    }

    public static String byteToString(byte[] bArr, int i) {
        StringBuffer stringBuffer = new StringBuffer("");
        for (int i2 = 0; i2 < i; i2++) {
            stringBuffer.append(Integer.toHexString(bArr[i2] & 255));
        }
        return stringBuffer.toString();
    }

    public static String binary(byte[] bytes, int radix){
        return new BigInteger(1 , bytes).toString(radix);
    }
}
