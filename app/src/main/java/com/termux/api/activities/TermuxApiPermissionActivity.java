package com.termux.api.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.termux.api.TermuxAPIConstants;
import com.termux.api.util.ResultReturner;
import com.termux.shared.android.PermissionUtils;

import java.util.ArrayList;

public class TermuxApiPermissionActivity extends Activity {

    private static final int REQUEST_API_PERMISSIONS = 1;

    /**
     * Intent extra containing the permissions to request.
     */
    private static final String PERMISSIONS_EXTRA = TermuxAPIConstants.API_NAMESPACE + ".permissions";

    /**
     * Check for and request permissions if necessary.
     *
     * @return if all permissions were already granted
     */
    public static boolean checkAndRequestPermissions(Context context, Intent intent, String... permissions) {
        final ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (!PermissionUtils.checkPermission(context, permission)) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) return true;
        requestPermissions(context, intent, permissionsToRequest);
        return false;
    }

    public static boolean checkAndRequestPreciseLocation(Context context, Intent intent) {
        if (PermissionUtils.checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) return true;

        ArrayList<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requestPermissions(context, intent, permissions);
        return false;
    }

    private static void requestPermissions(Context context, Intent intent, ArrayList<String> permissions) {
        ResultReturner.returnError(context, intent,
            "Grant Termux the required permissions and retry: " + TextUtils.join(", ", permissions));

        Intent startIntent = new Intent(context, TermuxApiPermissionActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putStringArrayListExtra(PERMISSIONS_EXTRA, permissions);
        context.startActivity(startIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return;
        ArrayList<String> permissionValues = getIntent().getStringArrayListExtra(PERMISSIONS_EXTRA);
        if (permissionValues != null) {
            for (String permission : permissionValues) {
                if (!PermissionUtils.checkPermission(this, permission)) {
                    if (PermissionUtils.requestPermissions(this, permissionValues.toArray(new String[0]), REQUEST_API_PERMISSIONS)) {
                        return;
                    }
                    break;
                }
            }
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_API_PERMISSIONS) finish();
    }
}
