package util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileUtils {
    private static InputStream getFileDecodedStream(File file) throws IOException, CompressorException {
        // Handle .zip, read only the first file entry of zip file.
        if (file.getName().toLowerCase().endsWith(".zip")) {
            ZipArchiveInputStream zipInput =
                    new ZipArchiveInputStream(new FileInputStream(file));
            ZipArchiveEntry currentEntry;
            while ((currentEntry = zipInput.getNextZipEntry()) != null) {
                if (currentEntry.isDirectory()) {
                    continue;
                }
                return zipInput;
            }
            return null;
        }

        // Handle .tar.gz, read only the first file entry of tar file.
        if (file.getName().toLowerCase().endsWith(".tar.gz")) {
            TarArchiveInputStream tarInput =
                    new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(file)));
            TarArchiveEntry currentEntry;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.isDirectory()) {
                    continue;
                }
                return tarInput;
            }
            return null;
        }

        // Everything else.
        int dot = file.getName().lastIndexOf(".");
        String extension = dot == -1 ? null : file.getName().substring(dot + 1).toLowerCase();

        InputStream stream = new FileInputStream(file);
        if (extension == null) {
            return stream;
        }
        switch (extension) {
            case "gz":
                return new GZIPInputStream(stream);
            case "bz2":
                return new BZip2CompressorInputStream(stream);
            // More extensions here.
            default:
                return stream;
        }
    }

    private static InputStream getResourceDecodedStream(String file) throws IOException, CompressorException {
        // Handle .zip, read only the first file entry of zip file.
        if (file.toLowerCase().endsWith(".zip")) {
            ZipArchiveInputStream zipInput =
                    new ZipArchiveInputStream(FileUtils.class.getClassLoader().getResourceAsStream(file));
            ZipArchiveEntry currentEntry;
            while ((currentEntry = zipInput.getNextZipEntry()) != null) {
                if (currentEntry.isDirectory()) {
                    continue;
                }
                return zipInput;
            }
            return null;
        }

        // Handle .tar.gz, read only the first file entry of tar file.
        if (file.toLowerCase().endsWith(".tar.gz")) {
            TarArchiveInputStream tarInput =
                    new TarArchiveInputStream(new GZIPInputStream(FileUtils.class.getClassLoader().getResourceAsStream(file)));
            TarArchiveEntry currentEntry;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.isDirectory()) {
                    continue;
                }
                return tarInput;
            }
            return null;
        }

        // Everything else.
        int dot = file.lastIndexOf(".");
        String extension = dot == -1 ? null : file.substring(dot + 1).toLowerCase();

        InputStream stream = FileUtils.class.getClassLoader().getResourceAsStream(file);
        if (extension == null) {
            return stream;
        }
        switch (extension) {
            case "gz":
                return new GZIPInputStream(stream);
            case "bz2":
                return new BZip2CompressorInputStream(stream);
            // More extensions here.
            default:
                return stream;
        }
    }

    private static OutputStream getFileEncodedStream(File file) throws IOException, CompressorException {
        int dot = file.getName().lastIndexOf(".");
        String extension = dot == -1 ? null : file.getName().substring(dot + 1).toLowerCase();

        OutputStream stream = new FileOutputStream(file);
        if (extension == null) {
            return stream;
        }
        switch (extension) {
            case "gz":
                return new GZIPOutputStream(stream);
            case "bz2":
                return new BZip2CompressorOutputStream(stream);
            // More extensions here.
            default:
                return stream;
        }
    }

    public static ArrayList<String> getLines(File file, Charset charset) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(getFileDecodedStream(file), charset))) {
            ArrayList<String> list = new ArrayList<>();
            String line = null;
            while ((line = in.readLine()) != null) {
                list.add(line);
            }
            list.trimToSize();
            return list;
        } catch (IOException | CompressorException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ArrayList<String> getLines(String file) {
        return getLines(new File(file), Charset.defaultCharset());
    }

    public static ArrayList<String> getLines(String file, String charset) {
        return getLines(new File(file), Charset.forName(charset));
    }

    public static LineStream getLineStream(File file, Charset charset) {
        try {
            return new LineStream(getFileDecodedStream(file), charset);
        } catch (IOException | CompressorException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static LineStream getLineStream(String file) {
        return getLineStream(new File(file), Charset.defaultCharset());
    }

    public static LineStream getLineStream(String file, String charset) {
        return getLineStream(new File(file), Charset.forName(charset));
    }

    public static PrintWriter getPrintWriter(File file, Charset charset) {
        try {
            return new PrintWriter(new BufferedWriter(new OutputStreamWriter(getFileEncodedStream(file), charset)));
        } catch (IOException | CompressorException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static PrintWriter getPrintWriter(String file) {
        return getPrintWriter(new File(file), Charset.defaultCharset());
    }

    public static PrintWriter getPrintWriter(String file, String charset) {
        return getPrintWriter(new File(file), Charset.forName(charset));
    }

    public static String getContent(InputStream inputStream, String charsetName) {
        try {
            ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int readByte = 0;
            while ((readByte = inputStream.read(buffer, 0, buffer.length)) != -1) {
                contentBytes.write(buffer, 0, readByte);
            }
            return contentBytes.toString(charsetName);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getContent(File file, Charset charset) {
        try {
            InputStream inStream = getFileDecodedStream(file);
            ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int readByte = 0;
            while ((readByte = inStream.read(buffer, 0, buffer.length)) != -1) {
                contentBytes.write(buffer, 0, readByte);
            }
            return contentBytes.toString(charset.name());
        } catch (IOException | CompressorException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getContent(String file) {
        return getContent(new File(file), Charset.defaultCharset());
    }

    public static String getContent(String file, String charset) {
        return getContent(new File(file), Charset.forName(charset));
    }

    public static class LineStream implements Iterable<String> {

        private BufferedReader in = null;
        private boolean autoClose;

        public LineStream(InputStream inputStream, Charset charset, boolean autoClose) {
            in = new BufferedReader(new InputStreamReader(inputStream, charset));
            this.autoClose = autoClose;
        }

        public LineStream(InputStream inputStream, Charset charset) {
            this(inputStream, charset, true);
        }

        public String readLine() {
            try {
                return in == null ? null : in.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<String>() {
                private String currentLine = null;

                @Override
                public boolean hasNext() {
                    try {
                        return in != null && (currentLine = in.readLine()) != null;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                @Override
                public String next() {
                    return currentLine;
                }
            };
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (autoClose && in != null) {
                    in.close();
                }
            } catch (IOException e) {
                // Do nothing.
            }
            super.finalize();
        }
    }

    public static LineStream getResourceLineStream(String file, String charset) {
        try {
            return new LineStream(getResourceDecodedStream(file), Charset.forName(charset));
        } catch (IOException | CompressorException e) {
            e.printStackTrace();
            return null;
        }
    }
}
