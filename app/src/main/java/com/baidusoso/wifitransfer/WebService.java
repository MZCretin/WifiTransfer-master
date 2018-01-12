package com.baidusoso.wifitransfer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import com.hwangjr.rxbus.RxBus;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.body.MultipartFormDataBody;
import com.koushikdutta.async.http.body.Part;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;

import timber.log.Timber;

public class WebService extends Service {

    static final String ACTION_START_WEB_SERVICE = "com.baidusoso.wifitransfer.action.START_WEB_SERVICE";
    static final String ACTION_STOP_WEB_SERVICE = "com.baidusoso.wifitransfer.action.STOP_WEB_SERVICE";

    private static final String TEXT_CONTENT_TYPE = "text/html;charset=utf-8";
    private static final String CSS_CONTENT_TYPE = "text/css;charset=utf-8";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";
    private static final String JS_CONTENT_TYPE = "application/javascript";
    private static final String PNG_CONTENT_TYPE = "application/x-png";
    private static final String JPG_CONTENT_TYPE = "application/jpeg";
    private static final String SWF_CONTENT_TYPE = "application/x-shockwave-flash";
    private static final String WOFF_CONTENT_TYPE = "application/x-font-woff";
    private static final String TTF_CONTENT_TYPE = "application/x-font-truetype";
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";
    private static final String EOT_CONTENT_TYPE = "image/vnd.ms-fontobject";
    private static final String MP3_CONTENT_TYPE = "audio/mp3";
    private static final String MP4_CONTENT_TYPE = "video/mpeg4";
    FileUploadHolder fileUploadHolder = new FileUploadHolder();
    private AsyncHttpServer server = new AsyncHttpServer();
    private AsyncServer mAsyncServer = new AsyncServer();

    public static void start(Context context) {
        Intent intent = new Intent(context, WebService.class);
        intent.setAction(ACTION_START_WEB_SERVICE);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, WebService.class);
        intent.setAction(ACTION_STOP_WEB_SERVICE);
        context.startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_WEB_SERVICE.equals(action)) {
                startServer();
            } else if (ACTION_STOP_WEB_SERVICE.equals(action)) {
                stopSelf();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
        if (mAsyncServer != null) {
            mAsyncServer.stop();
        }
    }

    private void startServer() {
        server.get("/images/.*", this::sendResources);
        server.get("/scripts/.*", this::sendResources);
        server.get("/css/.*", this::sendResources);
        //index page
        server.get("/", (AsyncHttpServerRequest request, AsyncHttpServerResponse response) -> {
            try {
                response.send(getIndexContent());
            } catch (IOException e) {
                e.printStackTrace();
                response.code(500).end();
            }
        });
        //query upload list
        server.get("/files", (AsyncHttpServerRequest request, AsyncHttpServerResponse response) -> {
            JSONArray array = new JSONArray();
            File dir = Constants.DIR;
            if (dir.exists() && dir.isDirectory()) {
                String[] fileNames = dir.list();
                if (fileNames != null) {
                    for (String fileName : fileNames) {
                        File file = new File(dir, fileName);
                        if (file.exists() && file.isFile()) {
                            try {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("name", fileName);
                                long fileLen = file.length();
                                DecimalFormat df = new DecimalFormat("0.00");
                                if (fileLen > 1024 * 1024) {
                                    jsonObject.put("size", df.format(fileLen * 1f / 1024 / 1024) + "MB");
                                } else if (fileLen > 1024) {
                                    jsonObject.put("size", df.format(fileLen * 1f / 1024) + "KB");
                                } else {
                                    jsonObject.put("size", fileLen + "B");
                                }
                                array.put(jsonObject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            response.send(array.toString());
        });
        //delete
        server.post("/files/.*", (AsyncHttpServerRequest request, AsyncHttpServerResponse response) -> {
            final UrlEncodedFormBody body = (UrlEncodedFormBody) request.getBody();
            if ("delete".equalsIgnoreCase(body.get().getString("_method"))) {
                String path = request.getPath().replace("/files/", "");
                try {
                    path = URLDecoder.decode(path, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                File file = new File(Constants.DIR, path);
                if (file.exists() && file.isFile()) {
                    file.delete();
                    RxBus.get().post(Constants.RxBusEventType.LOAD_BOOK_LIST, 0);
                }
            }
            response.end();
        });
        //download
        server.get("/files/.*", (AsyncHttpServerRequest request, AsyncHttpServerResponse response) -> {
            String path = request.getPath().replace("/files/", "");
            try {
                path = URLDecoder.decode(path, "utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            File file = new File(Constants.DIR, path);
            if (file.exists() && file.isFile()) {
                try {
                    response.getHeaders().add("Content-Disposition", "attachment;filename=" + URLEncoder.encode(file.getName(), "utf-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                response.sendFile(file);
                return;
            }
            response.code(404).send("Not found!");
        });
        //upload
        server.post("/files", (AsyncHttpServerRequest request, AsyncHttpServerResponse response) -> {
                    final MultipartFormDataBody body = (MultipartFormDataBody) request.getBody();
                    body.setMultipartCallback((Part part) -> {
                        if (part.isFile()) {
                            body.setDataCallback((DataEmitter emitter, ByteBufferList bb) -> {
                                fileUploadHolder.write(bb.getAllByteArray());
                                bb.recycle();
                            });
                        } else {
                            if (body.getDataCallback() == null) {
                                body.setDataCallback((DataEmitter emitter, ByteBufferList bb) -> {
                                    try {
                                        String fileName = URLDecoder.decode(new String(bb.getAllByteArray()), "UTF-8");
                                        fileUploadHolder.setFileName(fileName);
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                    bb.recycle();
                                });
                            }
                        }
                    });
                    request.setEndCallback((Exception e) -> {
                        fileUploadHolder.reset();
                        response.end();
                        RxBus.get().post(Constants.RxBusEventType.LOAD_BOOK_LIST, 0);
                    });
                }
        );
        server.get("/progress/.*", (final AsyncHttpServerRequest request,
                                    final AsyncHttpServerResponse response) -> {
                    JSONObject res = new JSONObject();

                    String path = request.getPath().replace("/progress/", "");

                    if (path.equals(fileUploadHolder.fileName)) {
                        try {
                            res.put("fileName", fileUploadHolder.fileName);
                            res.put("size", fileUploadHolder.totalSize);
                            res.put("progress", fileUploadHolder.fileOutPutStream == null ? 1 : 0.1);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    response.send(res);
                }

        );
        server.listen(mAsyncServer, Constants.HTTP_PORT);
    }

    private String getIndexContent() throws IOException {
        BufferedInputStream bInputStream = null;
        try {
            bInputStream = new BufferedInputStream(getAssets().open("wifi/index.html"));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len = 0;
            byte[] tmp = new byte[10240];
            while ((len = bInputStream.read(tmp)) > 0) {
                baos.write(tmp, 0, len);
            }
            return new String(baos.toByteArray(), "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (bInputStream != null) {
                try {
                    bInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendResources(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
        try {
            String fullPath = request.getPath();
            fullPath = fullPath.replace("%20", " ");
            String resourceName = fullPath;
            if (resourceName.startsWith("/")) {
                resourceName = resourceName.substring(1);
            }
            if (resourceName.indexOf("?") > 0) {
                resourceName = resourceName.substring(0, resourceName.indexOf("?"));
            }
            if (!TextUtils.isEmpty(getContentTypeByResourceName(resourceName))) {
                response.setContentType(getContentTypeByResourceName(resourceName));
            }
            BufferedInputStream bInputStream = new BufferedInputStream(getAssets().open("wifi/" + resourceName));
            response.sendStream(bInputStream, bInputStream.available());
        } catch (IOException e) {
            e.printStackTrace();
            response.code(404).end();
            return;
        }
    }

    private String getContentTypeByResourceName(String resourceName) {
        if (resourceName.endsWith(".css")) {
            return CSS_CONTENT_TYPE;
        } else if (resourceName.endsWith(".js")) {
            return JS_CONTENT_TYPE;
        } else if (resourceName.endsWith(".swf")) {
            return SWF_CONTENT_TYPE;
        } else if (resourceName.endsWith(".png")) {
            return PNG_CONTENT_TYPE;
        } else if (resourceName.endsWith(".jpg") || resourceName.endsWith(".jpeg")) {
            return JPG_CONTENT_TYPE;
        } else if (resourceName.endsWith(".woff")) {
            return WOFF_CONTENT_TYPE;
        } else if (resourceName.endsWith(".ttf")) {
            return TTF_CONTENT_TYPE;
        } else if (resourceName.endsWith(".svg")) {
            return SVG_CONTENT_TYPE;
        } else if (resourceName.endsWith(".eot")) {
            return EOT_CONTENT_TYPE;
        } else if (resourceName.endsWith(".mp3")) {
            return MP3_CONTENT_TYPE;
        } else if (resourceName.endsWith(".mp4")) {
            return MP4_CONTENT_TYPE;
        }
        return "";
    }

    public class FileUploadHolder {
        private String fileName;
        private File recievedFile;
        private BufferedOutputStream fileOutPutStream;
        private long totalSize;


        public BufferedOutputStream getFileOutPutStream() {
            return fileOutPutStream;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
            totalSize = 0;
            if (!Constants.DIR.exists()) {
                Constants.DIR.mkdirs();
            }
            this.recievedFile = new File(Constants.DIR, this.fileName);
            Timber.d(recievedFile.getAbsolutePath());
            try {
                fileOutPutStream = new BufferedOutputStream(new FileOutputStream(recievedFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public void reset() {
            if (fileOutPutStream != null) {
                try {
                    fileOutPutStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            fileOutPutStream = null;
        }

        public void write(byte[] data) {
            if (fileOutPutStream != null) {
                try {
                    fileOutPutStream.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            totalSize += data.length;
        }
    }
}
