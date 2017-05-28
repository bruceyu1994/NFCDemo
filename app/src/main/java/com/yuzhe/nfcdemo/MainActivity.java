package com.yuzhe.nfcdemo;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements OnClick {
    NfcAdapter nfcAdapter;
    @Bind(R.id.textView)
    TextView textView;
    @Bind(R.id.scrollView)
    ScrollView scrollView;
    @Bind(R.id.btn_Read)
    Button btnRead;
    @Bind(R.id.btn_Write)
    Button btnWrite;
    private String readResult;
    private String TAG = "nfcdemo";
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        //获取默认的NFC控制器
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            textView.setText("设备不支持NFC！");
        } else if (!nfcAdapter.isEnabled()) {
            textView.setText("请在系统设置中启用NFC功能");
        } else {
            textView.setText("系统NFC功能可正常使用");
        }

        // Create a generic PendingIntent that will be deliver to this activity. The NFC stack will fill in the intent with the details of the discovered tag before delivering to this activity.
        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        // 做一个IntentFilter过滤你想要的action 这里过滤的是ndef
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        //如果你对action的定义有更高的要求以，比如data的要求，你可使用如下的代码来定义intentFilter
//        try {
//            ndef.addDataType("*/*");
//        } catch (MalformedMimeTypeException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
        //生成intentFilter
        mFilters = new IntentFilter[]{
                ndef,
        };
        // 做一个tech-list。可以看到是二维数据，每一个一维数组之间的关系是或，但是一个一维数组之内的各个项就是与的关系了
        mTechLists = new String[][]{
                new String[]{NfcF.class.getName()},
                new String[]{NfcA.class.getName()},
                new String[]{NfcB.class.getName()},
                new String[]{NfcV.class.getName()}
        };
    }

    @OnClick(R.id.btn_Read)
    public void readNFC() {
        Toast.makeText(this, "start to read NFC", Toast.LENGTH_LONG).show();
        Log.d(TAG, "start to read NFC");
        readFromTag(getIntent());
    }

    @OnClick(R.id.btn_Write)
    public void writeNFC() {
        Toast.makeText(this, "start to write NFC", Toast.LENGTH_LONG).show();
        Log.d(TAG, "start to write NFC");
        wirteToTag(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        //设定intentfilter和tech-list。如果两个都为null就代表优先接收任何形式的TAG action。也就是说系统会主动发TAG intent。
        nfcAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //反注册
        nfcAdapter.disableForegroundDispatch(this);
    }

    //字符序列转换为16进制字符串
    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("0x");
        if (src == null || src.length <= 0) {
            return null;
        }
        char[] buffer = new char[2];
        for (int i = 0; i < src.length; i++) {
            buffer[0] = Character.forDigit((src[i] >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(src[i] & 0x0F, 16);
            System.out.println(buffer);
            stringBuilder.append(buffer);
        }
        return stringBuilder.toString();
    }

    private void processIntent(Intent intent) {
        //取出封装在Intent中的Tag
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        for (String tech : tagFromIntent.getTechList()) {
            System.out.print(tech);
        }

        boolean auth = false;
        //读取TAG
        MifareClassic mfc = MifareClassic.get(tagFromIntent);
        try {
            String metaInfo = "";
            // Enable I/O operations to the tag from this TagTechnology object
            mfc.connect();
            int type = mfc.getType(); //获取Tag类型
            int sectorCount = mfc.getSectorCount(); //获取Tag中包含的扇区数
            String typeS = "";
            switch (type) {
                case MifareClassic.TYPE_CLASSIC:
                    typeS = "TYPE_CLASSIC";
                    break;
                case MifareClassic.TYPE_PLUS:
                    typeS = "TYPE_PLUS";
                    break;
                case MifareClassic.TYPE_PRO:
                    typeS = "TYPE_PRO";
                    break;
                case MifareClassic.TYPE_UNKNOWN:
                    typeS = "TYPE_UNKNOWN";
                    break;
            }
            metaInfo += "卡片类型：" + typeS + "\n共" + sectorCount + "个扇区\n共"
                    + mfc.getBlockCount() + "个块\n存储空间:" + mfc.getSize() + "B\n";

            for (int i = 0; i < sectorCount; i++) {
                //Authenticate a sector with key A
                auth = mfc.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT);
                int bCount;
                int bIndex;
                if (auth) {
                    metaInfo += "Sector " + i + "验证成功\n";
                    //读取扇区中的块
                    bCount = mfc.getBlockCountInSector(i);
                    bIndex = mfc.sectorToBlock(i);
                    for (int j = 0; j < bCount; j++) {
                        byte[] data = mfc.readBlock(bIndex);
                        metaInfo += "Block " + bIndex + " :"
                                + bytesToHexString(data) + "\n";
                        bIndex++;
                    }
                } else {
                    metaInfo += "Sector" + i + ":验证失败\n";
                }
            }
            textView.setText(metaInfo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean readFromTag(Intent intent) {
        Parcelable[] rawArray = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage mNdefMsg = (NdefMessage) rawArray[0];
        NdefRecord mNdefRecord = mNdefMsg.getRecords()[0];
        try {
            if (mNdefRecord != null) {
                readResult = new String(mNdefRecord.getPayload(), "UTF-8");
                Toast.makeText(this, readResult, Toast.LENGTH_LONG).show();
                return true;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        ;
        return false;
    }

    private boolean wirteToTag(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Ndef ndef = Ndef.get(tag);
        try {
            ndef.connect();
            NdefRecord ndefRecord = createTextRecord("nfc测试", Locale.US, true);
            NdefRecord[] records = {ndefRecord};
            NdefMessage ndefMessage = new NdefMessage(records);
            ndef.writeNdefMessage(ndefMessage);
            return true;
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            return false;
        } catch (FormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    public NdefRecord createTextRecord(String payload, Locale locale, boolean encodeInUtf8) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));
        Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = payload.getBytes(utfEncoding);
        int utfBit = encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);
        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT, new byte[0], data);
        return record;
    }

    @Override
    public int[] value() {
        return new int[0];
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return null;
    }
}
