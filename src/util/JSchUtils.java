package util;

import com.jcraft.jsch.*;

import java.io.InputStream;
import java.nio.charset.Charset;

public class JSchUtils {
    private static Session session = null;
    private static ChannelSftp channel = null;

    private static boolean start() {
        if (session != null) {
            return true;
        }
        try {
            session = new JSch().getSession("hvthinh", "sedna", 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword("1234abcdABCD");

            session.connect();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            return true;
        } catch (JSchException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static FileUtils.LineStream getLineStreamFromServer(String file, Charset charset) {
        if (channel == null) {
            if (!start()) {
                return null;
            }
        }
        try {
            return new FileUtils.LineStream(channel.get(file), charset);
        } catch (SftpException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static InputStream getFileInputStreamFromServer(String file) {
        if (channel == null) {
            if (!start()) {
                return null;
            }
        }
        try {
            return channel.get(file);
        } catch (SftpException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean stop() {
        try {
            channel.exit();
            session.disconnect();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
