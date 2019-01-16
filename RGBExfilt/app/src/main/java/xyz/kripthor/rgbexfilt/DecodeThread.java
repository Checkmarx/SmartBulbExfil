package xyz.kripthor.rgbexfilt;

import android.hardware.Camera;
import android.util.Log;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;

/**
 * Created by kripthor on 23-11-2017.
 */

public class DecodeThread extends Thread {

    private long lastPrint = 0;
    private long lastDataTime = 0;
    private String lastDataString;
    private static long PRINTINTERVAL = 500;
    private int width, height;
    private double avgr,avgg,avgb;
    private double zerob = 0;
    private double oneb = 255;
    private ArrayList<DataPoint> rawData;
    private int TRANSMIT_MS = 200;
    private String statusString;
    private String dataString;
    private boolean running;
    private int frameCounter = 0;
    private Camera mCamera;
    public boolean alive;

    public ArrayList frames;

    //HEAVY OPTIMIZATIONS NEEDED!


    public DecodeThread(Camera cam, int width, int height) {
        lastPrint = System.currentTimeMillis();
        this.width = width;
        this.height = height;
        zeroStats();
        mCamera = cam;
        frames = new ArrayList();
        statusString = new String();
        dataString = new String();
        lastDataString = new String();
    }

    public void zeroStats() {
        avgr=avgg=avgb=0;
        zerob = 0;
        oneb = 255;
        rawData = new ArrayList<>();
    }

    public void addFrame(byte[] bytes) {
        synchronized (frames) {
            int toStore[] = shrink(bytes,width,height);
            frames.add(toStore);
        }
    }

    public void run() {
        alive = true;
        boolean hasFrames = false;
        while (alive) {
            int bytes[];
            hasFrames = false;
            synchronized (frames) {
                if (frames.size() > 0) hasFrames = true;
            }
            if (hasFrames) {
                synchronized (frames) {
                    bytes = (int[]) frames.remove(0);
                }
                onPreviewFrame(bytes,mCamera);
            } else {
                try {
                    sleep(25);
                   } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void die() {
        alive = false;
    }

    public void onPreviewFrame(int[] bytes, Camera camera) {

        long now = System.currentTimeMillis();


            if (lastDataString.equals(dataString)) {
                if (now - lastDataTime > 12000) {
                    String decodedData = decodeData();
                    String decodedAsciiData = decodeAscii(decodedData);
                    Log.d("RGB rawData2", rawData());
                    Log.d("RGB data2", decodedData);
                    Log.d("RGB ascii2", decodedAsciiData);
                    this.zeroStats();
                    lastDataTime = now;
                    lastDataString = dataString;
                }
            } else {
                lastDataTime = now;
                lastDataString = dataString;
            }
/*
        frameCounter++;
        if (now-lastPrint > PRINTINTERVAL) {

            Log.d("RGB TEST fps",""+frameCounter*1.0/((now-lastPrint)/1000.0));
            statusView.setText("FPS: "+frameCounter*1.0/((now-lastPrint)/1000.0));
            lastPrint = now;
            frameCounter = 0;
        }
        if (true) return;
*/
      //  int argb[] = new int [height*width];
    //    YUV_NV21_TO_RGB(argb,bytes,width,height);
        calcAvg(bytes);
        zerob = Math.max(zerob,avgb);
        oneb = Math.min(oneb,avgb);
        int currentBit = decodeCurrent();
        if (currentBit > -1) {
            rawData.add(new DataPoint(now,currentBit));
        }

        if (now-lastPrint > PRINTINTERVAL && rawData.size() > 2 && !running) {
            int fps = (int)(rawData.size()/((rawData.get(rawData.size()-1).arrived - rawData.get(0).arrived)/1000.0));
            Log.d("FPS_RGB", fps + " R "+(int)avgr+ " G "+(int)avgg+" B "+(int)avgb);
            Log.d("RGB vals","Max: "+(int)zerob+" Min: "+(int)oneb+"  -- bit: " + currentBit);
            Log.d("RGB rawData", rawData());
            synchronized (statusString) {
                statusString = ("FPS: " + fps + " R" + (int) avgr + " G" + (int) avgg + " B" + (int) avgb + " | maxB:" + (int) zerob + " minB:" + (int) oneb + " bit:" + currentBit);
            }
            lastPrint = now;
        }

        if (now-lastPrint > PRINTINTERVAL && rawData.size() > 2 && running) {
            String decodedData = decodeData();
            String decodedAsciiData = decodeAscii(decodedData);
            int fps = (int)(rawData.size()/((rawData.get(rawData.size()-1).arrived - rawData.get(0).arrived)/1000.0));
            Log.d("FPS_RGB", fps + " R "+(int)avgr+ " G "+(int)avgg+" B "+(int)avgb);
            Log.d("RGB vals","Max: "+(int)zerob+" Min: "+(int)oneb+"  -- Current bit: " + currentBit);
            Log.d("RGB rawData", rawData());
            Log.d("RGB data", decodedData);
            Log.d("RGB ascii", decodedAsciiData);
            synchronized (statusString) {
                statusString = ("FPS: "+fps+ " R"+(int)avgr+ " G"+(int)avgg+" B"+(int)avgb + " | maxB:"+(int)zerob+" minB:"+(int)oneb+" bit:" + currentBit);
            }
            synchronized (dataString) {
                dataString = (decodedData+"\n\n"+decodedAsciiData+"\n");
            }
            lastPrint = now;
        }


    }



    private String rawData() {
        StringBuilder sb = new StringBuilder();
        for (DataPoint d: rawData) sb.append(d.bit);
        return sb.toString();
    }

    private String decodeData() {
        StringBuilder sb = new StringBuilder();
        sb.append("");
        if (rawData == null || rawData.size() < 2) return "";
        DataPoint d;

        long last = 0;
        int bit = 0;
        int counter = 0;
        int i = 0;

        while (i < rawData.size()) {


            boolean foundPreamble = false;

            while (!foundPreamble) {
                long preambleStart, preambleHalf, preambleStop;
                //Find preamble
                while (i < rawData.size() && rawData.get(i).bit == 0) i++;
                if (i >= rawData.size()) return sb.toString();
                preambleStart = rawData.get(i).arrived;
                while (i < rawData.size() && rawData.get(i).bit == 1) i++;
                if (i >= rawData.size())  return sb.toString();
                preambleHalf = rawData.get(i).arrived;
                if (preambleHalf - preambleStart > 1600) {
                     sb.append("F");
                    //found first part of 1111s with 700ms duration, search for 000s with 500ms duration
                    while (i < rawData.size() && (rawData.get(i).bit == 0 && rawData.get(i).arrived - preambleHalf < 400)) i++;
                    if (i >= rawData.size()) return sb.toString();
                    preambleStop = rawData.get(i).arrived;
                    if (preambleStop - preambleHalf  > 300) {
                        sb.append("S");
                        foundPreamble = true;
                    }
                }
            }
            sb.append(" ");
            counter = 0;

            long startByte = rawData.get(i).arrived;
            last = startByte;
            bit = 0;
            int bitWindow = 1;
            //Decode
            while (i < rawData.size() && rawData.get(i).arrived < startByte+8*TRANSMIT_MS) {
                d = rawData.get(i);
                if (d.arrived > startByte+bitWindow*TRANSMIT_MS) {
                    sb.append(Math.round(bit * 1.0 / counter));
                    last = d.arrived;
                    bit = 0;
                    counter = 0;
                    bitWindow++;
                    continue;
                } else {
                    bit += d.bit;
                    counter++;
                }
                i++;
            }
            sb.append(Math.round(bit * 1.0 / counter));
        }
        return sb.toString();
    }

    private String decodeAscii(String decodedData) {
        StringBuilder sb = new StringBuilder();
        sb.append("");
        String chars[] = decodedData.split(" ");
        if (chars.length < 1) return sb.toString();
        for (String c:chars) {
            if (c.length() < 7) continue;
            int code = 0;
            for (int i = 0; i < c.length(); i++) {
                if (c.charAt(i) == '1') {
                    code += 1<<i;
                }
            }
            sb.append((char)code);
        }
        return sb.toString();

    }

    private int decodeCurrent() {
        if (Math.abs(zerob - oneb) < 2) return -1;
        if (avgb - oneb > zerob - avgb) return 0;
        return 1;
    }

    /*
    public void YUV_NV21_TO_RGB(int[] argb, byte[] yuv, int width, int height) {
        final int frameSize = width * height;

        final int ii = 0;
        final int ij = 0;
        final int di = +1;
        final int dj = +1;

        int a = 0;
        long t = 0;
        for (int i = 0, ci = ii; i < height; ++i, ci += di) {
            for (int j = 0, cj = ij; j < width; ++j, cj += dj) {
                int y = (0xff & ((int) yuv[ci * width + cj]));
                int v = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 0]));
                int u = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 1]));
                y = y < 16 ? 16 : y;

                int r = (int) (1.164f * (y - 16) + 1.596f * (v - 128));
                int g = (int) (1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = (int) (1.164f * (y - 16) + 2.018f * (u - 128));

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                argb[a++] = 0xff000000 | (r << 16) | (g << 8) | b;

                if (j > (int)(width*.40) && j < (int)(width*.60) && i > (int)(height*.40) && i < (int)(height*.60)) {
                    t++;
                    avgr += r;
                    avgg += g;
                    avgb += b;
                }

            }
        }
        avgr /= t;
        avgg /= t;
        avgb /= t;
    }
    */

    public int[] shrink(byte[] yuv, int width, int height) {
        final int frameSize = width * height;
        int result[] = new int[(int)(Math.ceil(width*.1) * Math.ceil(height*.1))];
        final int ii = 0;
        final int ij = 0;
        final int di = +1;
        final int dj = +1;

        int a = 0;
        long t = 0;
        for (int i = 0, ci = ii; i < height; ++i, ci += di) {
            for (int j = 0, cj = ij; j < width; ++j, cj += dj) {

                if (j > (int)(width*.45) && j < (int)(width*.55) && i > (int)(height*.45) && i < (int)(height*.55)) {
                    int y = (0xff & ((int) yuv[ci * width + cj]));
                    int v = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 0]));
                    int u = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 1]));
                    y = y < 16 ? 16 : y;
                    int r = (int) (1.164f * (y - 16) + 1.596f * (v - 128));
                    int g = (int) (1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                    int b = (int) (1.164f * (y - 16) + 2.018f * (u - 128));
                    r = r < 0 ? 0 : (r > 255 ? 255 : r);
                    g = g < 0 ? 0 : (g > 255 ? 255 : g);
                    b = b < 0 ? 0 : (b > 255 ? 255 : b);
                    result[a++] = 0xff000000 | (r << 16) | (g << 8) | b;
                    if (a >= result.length) {
                        j = i = 999999999;
                    }
                }

            }
        }

        return result;
    }


    private void calcAvg(int[] bytes) {
        int c;
        int t = bytes.length;
        for (int i = 0; i < t; i++) {
            c = bytes[i];
            avgr += ((c & 0x00ff0000) >> 16);
            avgg += ((c & 0x0000ff00) >> 8);
            avgb += ((c & 0x000000ff));
        }
        avgr /= t;
        avgg /= t;
        avgb /= t;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getDataString() {
        synchronized (dataString) {
            return dataString;
        }
    }

    public String getStatusString() {
        synchronized (statusString) {
            return statusString;
        }
    }
}
