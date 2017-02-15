/*
 * The MIT License
 *
 * Copyright (c) 2017, Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package me.justpitch.loggingbenchmarks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class CacheLineVolLog {
    private final String name;
    private final ThreadLocal<GregorianCalendar> cal = new ThreadLocal<GregorianCalendar>() {
        @Override
        protected GregorianCalendar initialValue() {
            return new GregorianCalendar();
        }
    };
    private final ThreadLocal<StringBuilder> builder = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder(1024);
        }
    };
    private volatile int i1;
    private volatile int i2;
    private volatile int i3;
    private volatile int i4;
    private volatile int i5;
    private volatile int i6;
    private volatile int i7;
    private volatile int i8;
    private volatile int i9;
    private volatile int enabled;
    private volatile int j1;
    private volatile int j2;
    private volatile int j3;
    private volatile int j4;
    private volatile int j5;
    private volatile int j6;
    private volatile int j7;
    private volatile int j8;
    private volatile int j9;
    private int charCount = 0;
    private BufferedOutputStream log;
    private File[] files;

    public CacheLineVolLog(Class clazz) {
        this.name = clazz.getName();
        files = new File[5];
        for (int i = 0; i < files.length; i++) {
            files[i] = new File(String.format("java%d.log", i + 1));
        }
        rotate();
        Thread thread = new Thread(new Enabler());
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void rotate() {
        if (log != null) {
            try {
                log.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (files[files.length - 1].isFile()) {
            files[files.length - 1].delete();
        }
        for (int i = files.length - 2; i >= 0; i--) {
            if (files[i].isFile()) {
                files[i].renameTo(files[i + 1]);
            }
        }
        try {
            log = new BufferedOutputStream(new FileOutputStream(files[0]), 256 * 1024);
        } catch (FileNotFoundException e) {
            // ignore
        }
        charCount = 0;
    }

    public void info(String message, Object arg0) {
        if (enabled==0) {
            return;
        }
        _info(message, arg0);
    }

    public void info(String message, Object arg0, Object arg1) {
        if (enabled==0) {
            return;
        }
        _info(message, arg0, arg1);
    }

    public void info(String message, Object... args) {
        if (enabled==0) {
            return;
        }
        _info(message, args);
    }

    private void _info(String message, Object... args) {
        StringBuilder msg = builder.get();
        msg.setLength(0);
        GregorianCalendar cal = this.cal.get();
        cal.setTimeInMillis(System.currentTimeMillis());
        msg.append(cal.get(Calendar.YEAR));
        msg.append('-');
        int MM = cal.get(Calendar.MONTH) + 1;
        if (MM < 10) {
            msg.append('0');
        }
        msg.append(MM);
        msg.append('-');
        int dd = cal.get(Calendar.DAY_OF_MONTH);
        if (dd < 10) {
            msg.append('0');
        }
        msg.append(dd);
        msg.append(' ');
        int HH = cal.get(Calendar.HOUR_OF_DAY);
        if (HH < 10) {
            msg.append('0');
        }
        msg.append(HH);
        msg.append(':');
        int mm = cal.get(Calendar.MINUTE);
        if (mm < 10) {
            msg.append('0');
        }
        msg.append(mm);
        msg.append(':');
        int ss = cal.get(Calendar.SECOND);
        if (ss < 10) {
            msg.append('0');
        }
        msg.append(ss);
        msg.append('.');
        int SSS = cal.get(Calendar.MILLISECOND);
        if (SSS < 100) {
            msg.append('0');
        }
        if (SSS < 10) {
            msg.append('0');
        }
        msg.append(SSS);
        msg.append(" [");
        msg.append(Thread.currentThread().getName());
        msg.append("] INFO ");
        msg.append(name);
        msg.append(" - ");
        int state = 0;
        int index = 0;
        for (char c : message.toCharArray()) {
            switch (state) {
                case 0:
                    switch (c) {
                        case '\\':
                            state = 1;
                            break;
                        case '{':
                            state = 2;
                            break;
                        default:
                            msg.append(c);
                            break;
                    }
                    break;
                case 1:
                    switch (c) {
                        case '{':
                        case '\\':
                            msg.append(c);
                            break;
                        default:
                            msg.append('\\');
                            msg.append(c);
                            break;
                    }
                    state = 0;
                    break;
                case 2:
                    if (c == '}') {
                        if (index < args.length) {
                            msg.append(args[index++]);
                        } else {
                            msg.append("{}");
                        }
                    } else {
                        msg.append('{');
                        msg.append(c);
                    }
                    state = 0;
                    break;
            }
        }
        msg.append('\n');
        try {
            byte[] bytes = msg.toString().getBytes(StandardCharsets.UTF_8);
            synchronized (this) {
                charCount += bytes.length;
                log.write(bytes);
                if (charCount > 500000) {
                    rotate();
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private class Enabler implements Runnable {

        @Override
        public void run() {
            while (true) {
                enabled = new File("log.enabled").isFile() ? 1 : 0;
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
