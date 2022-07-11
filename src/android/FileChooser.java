package com.megster.cordova;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FileChooser extends CordovaPlugin {

    private static final String TAG = "FileChooser";
    private static final String ACTION_OPEN = "open";
    private static final int PICK_FILE_REQUEST = 1;

    public static final String MIME = "mime";

    CallbackContext callback;

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {

        if (action.equals(ACTION_OPEN)) {
            JSONObject filters = inputs.optJSONObject(0);
            chooseFile(filters, callbackContext);
            return true;
        }

        return false;
    }

    public void chooseFile(JSONObject filter, CallbackContext callbackContext) {
        String uri_filter = filter.has(MIME) ? filter.optString(MIME) : "*/*";

        // type and title should be configurable

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(uri_filter);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        Intent chooser = Intent.createChooser(intent, "Select File");
        cordova.startActivityForResult(this, chooser, PICK_FILE_REQUEST);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callback = callbackContext;
        callbackContext.sendPluginResult(pluginResult);
    }

    @Override
    public void onDestroy() {
        clearCache(cordova.getContext());
        super.onDestroy();
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == PICK_FILE_REQUEST && callback != null) {

            if (resultCode == Activity.RESULT_OK) {

                Uri uri = data.getData();

                if (uri != null) {

                    Log.w(TAG, uri.toString());
                    
                    String path = uri.toString();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        path = getPath(cordova.getContext(), uri);
                    }
                    callback.success(path);

                } else {

                    callback.error("File uri was null");

                }

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // keep this string the same as in iOS document picker plugin
                // https://github.com/iampossible/Cordova-DocPicker
                callback.error("User canceled.");
            } else {

                callback.error(resultCode);
            }
        }
    }
    
    public static boolean clearCache(final Context context) {
        try {
            final File cacheDir = new File(context.getCacheDir() + "/file_picker/");
            final File[] files = cacheDir.listFiles();

            if (files != null) {
                for (final File file : files) {
                    file.delete();
                }
            }
        } catch (final Exception ex) {
            Log.e(TAG, "There was an error while clearing cached files: " + ex.toString());
            return false;
        }
        return true;
    }
    
    public static String getPath(final Context context, final Uri uri) {

        Log.i(TAG, "Caching from URI: " + uri.toString());
        FileOutputStream fos = null;
    
        final String fileName = getFileName(uri, context);
        final String path = context.getCacheDir().getAbsolutePath() + "/file_picker/" + (fileName != null ? fileName : new Random().nextInt(100000));
    
        final File file = new File(path);
    
        if(!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                fos = new FileOutputStream(path);
                try {
                    final BufferedOutputStream out = new BufferedOutputStream(fos);
                    final InputStream in = context.getContentResolver().openInputStream(uri);
                
                    final byte[] buffer = new byte[8192];
                    int len = 0;
                
                    while ((len = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, len);
                    }
                
                    out.flush();
                } finally {
                    fos.getFD().sync();
                }
            } catch (final Exception e) {
                try {
                    fos.close();
                } catch (final IOException | NullPointerException ex) {
                    Log.e(TAG, "Failed to close file streams: " + e.getMessage(), null);
                    return null;
                }
                Log.e(TAG, "Failed to retrieve path: " + e.getMessage(), null);
                return null;
            }
        }
    
        Log.d(TAG, "File loaded and cached at:" + path);
        return "file://" + path;
    }
    
    public static String getFileName(Uri uri, final Context context) {
        String result = null;
    
        try {
    
            if (uri.getScheme().equals("content")) {
                Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                    }
                } finally {
                    cursor.close();
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to handle file name: " + ex.toString());
        }
    
        return result;
    }
}
