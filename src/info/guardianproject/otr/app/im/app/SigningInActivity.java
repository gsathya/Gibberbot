/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.guardianproject.otr.app.im.app;

import info.guardianproject.otr.app.im.app.adapter.ConnectionListenerAdapter;
import info.guardianproject.otr.app.im.engine.ImConnection;
import info.guardianproject.otr.app.im.engine.ImErrorInfo;
import info.guardianproject.otr.app.im.plugin.BrandingResourceIDs;
import info.guardianproject.otr.app.im.provider.Imps;
import info.guardianproject.otr.app.im.service.ImServiceConstants;

import info.guardianproject.otr.app.im.R;
import info.guardianproject.otr.app.im.IChatSession;
import info.guardianproject.otr.app.im.IChatSessionManager;
import info.guardianproject.otr.app.im.IConnectionListener;
import info.guardianproject.otr.app.im.IImConnection;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

public class SigningInActivity extends Activity {
    private static final String SYNC_SETTINGS_ACTION = "android.settings.SYNC_SETTINGS";
    private static final String SYNC_SETTINGS_CATEGORY = "android.intent.category.DEFAULT";

    private IImConnection mConn;
    private IConnectionListener mListener;
    private SimpleAlertHandler mHandler;
    private ImApp mApp;
    private long mProviderId;
    private long mAccountId;
    private String mProviderName;

    private String mUserName;
    private String mPassword;

    private String mToAddress;
    
    private boolean isActive;
    
    private String mProxyType;
    private String mProxyHost;
    private int mProxyPort;

    protected static final int ID_CANCEL_SIGNIN = Menu.FIRST + 1;

    private Dialog dl = null;
    
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        //setTheme(android.R.style.Theme_Dialog);
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.signing_in_activity);
      
    }
    
    
    @Override
	protected void onResume() {
    	
		super.onResume();
		
		  Intent intent = getIntent();
	        mToAddress = intent.getStringExtra(ImApp.EXTRA_INTENT_SEND_TO_USER);

	        Uri data = intent.getData();
	        if (data == null) {
	            if(Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
	                log("Need account data to sign in");
	            }
	            finish();
	            return;
	        }
	
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(data, null, null, null, null);
        if (c == null) {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("Query fail:" + data);
            }
            finish();
            return;
        }
        if (!c.moveToFirst()) {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("No data for " + data);
            }
            c.close();
            finish();
            return;
        }

        mProviderId = c.getLong(c.getColumnIndexOrThrow(Imps.Account.PROVIDER));
        mAccountId = c.getLong(c.getColumnIndexOrThrow(Imps.Account._ID));
        mUserName = c.getString(c.getColumnIndexOrThrow(Imps.Account.USERNAME));
        
        mProxyType = intent.getStringExtra(ImApp.EXTRA_INTENT_PROXY_TYPE);
        if (mProxyType != null)
        {
        	mProxyHost = intent.getStringExtra(ImApp.EXTRA_INTENT_PROXY_HOST);
        	mProxyPort = intent.getIntExtra(ImApp.EXTRA_INTENT_PROXY_PORT,-1);
        }
        
        
        String pwExtra = intent.getStringExtra(ImApp.EXTRA_INTENT_PASSWORD);
        
        mPassword = pwExtra != null ? pwExtra
                : c.getString(c.getColumnIndexOrThrow(Imps.Account.PASSWORD));
        
        
        
        
        isActive = c.getInt(c.getColumnIndexOrThrow(Imps.Account.ACTIVE)) == 1;

        c.close();
        
        mApp = ImApp.getApplication(this);

        IImConnection conn = mApp.getConnection(mProviderId);

        try
        {
	        if (conn == null || (conn.getState() != ImConnection.LOGGED_IN))
	        {
	        	
	        	 if (mPassword == null || mPassword.length() == 0)
	        	 {
	        		 //show password prompt
	        		 showPasswordDialog();
	        	 }
	        	 else
	        	 {
	        		 gogo();
	        	 }
	        }
	        else
	        {
	        	  gogo();
	        }
	      
        }
        catch (Exception e)
        {
        	log("bad things: " + e);
        }
    }
    
    ProgressDialog pbarDialog;
    
    public void gogo()
    {
       
    	if (pbarDialog != null)
    		pbarDialog.dismiss();
    	
    	pbarDialog = new ProgressDialog( this );
    	pbarDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    	
    	String message = getString(R.string.signing_in_wait);
    	
    	pbarDialog.setMessage(message);
    	pbarDialog.show();
    	
        final ProviderDef provider = mApp.getProvider(mProviderId);
        mProviderName = provider.mName;

        /*
        BrandingResources brandingRes = mApp.getBrandingResource(mProviderId);
        getWindow().setFeatureDrawable(Window.FEATURE_LEFT_ICON,
                brandingRes.getDrawable(BrandingResourceIDs.DRAWABLE_LOGO));
*/
        
        setTitle(getResources().getString(R.string.signing_in_to,
                provider.mFullName));


        mHandler = new SimpleAlertHandler(this);
        mListener = new MyConnectionListener(mHandler);
        
        mApp.callWhenServiceConnected(mHandler, new Runnable() {
            public void run() {
                if (mApp.serviceConnected()) {
                    if (!isActive) {
                        activateAccount(mProviderId, mAccountId);
                    }
                    signInAccount();
                }
            }
        });

        // assume we can sign in successfully.
        setResult(RESULT_OK);
    }

	private void gotCredentials (String usr, String pwd)
	{
		this.mPassword = pwd;
		
		gogo();
	}
	private void showPasswordDialog() {
		
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Password");
		String message = getString(R.string.signin_password_prompt);
		alert.setMessage(message);
		
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
		input.setTransformationMethod(new PasswordTransformationMethod());
		alert.setView(input);
		alert.setNeutralButton("Remember",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String pwd = input.getText().toString();
						SharedPreferences prefs = PreferenceManager
								.getDefaultSharedPreferences(getApplication());
						Editor edit = prefs.edit();
						edit.putString("pref_account_pass", pwd);
						edit.commit();
						gotCredentials(null, pwd);
					}
				});

         alert.setPositiveButton("Login", new DialogInterface.OnClickListener() {  
         public void onClick(DialogInterface dialog, int whichButton) {  
        	 String pwd = input.getText().toString();  
        	 gotCredentials(null,pwd);
           }  
         });  

         alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {  
         public void onClick(DialogInterface dialog, int whichButton) {  
        	 
        	 finish();
             return;
           }  
        });  

        alert.show();  
	}
	
    
    @Override
    protected void onRestart() {
        super.onRestart();

        if (mApp.serviceConnected() && mApp.isBackgroundDataEnabled()) {
            signInAccount();
        } else {
            if(Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("onRestart: service disconnected or background data disabled...");
            }
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    void signInAccount() {
    	
    	boolean autoLoadContacts = true;
    	boolean autoRetryLogin = false;
    	
        try {
            IImConnection conn = mApp.getConnection(mProviderId);
            if (conn != null) {
                mConn = conn;
                // register listener before get state so that we won't miss
                // any state change event.
                conn.registerConnectionListener(mListener);
                int state = conn.getState();
                if (state != ImConnection.LOGGING_IN) {
                    // already signed in or failed
                    conn.unregisterConnectionListener(mListener);
                    handleConnectionEvent(state, null);
                }
            } else {
                if (mApp.isBackgroundDataEnabled()) {
                    mConn = mApp.createConnection(mProviderId);
                    mConn.registerConnectionListener(mListener);
                    
                    mConn.setProxy(mProxyType, mProxyHost, mProxyPort);
                 	mConn.login(mAccountId, mUserName, mPassword, autoLoadContacts, autoRetryLogin);
                 	
                    
                } else {
                    promptForBackgroundDataSetting();
                    return;
                }
            }

        } catch (RemoteException e) {
        	pbarDialog.dismiss();
        	
            mHandler.showServiceErrorAlert();
            finish();
        }
    }

    private void activateAccount(long providerId, long accountId) {
        // Update the active value. We restrict to only one active
        // account per provider right now, so update all accounts of
        // this provider to inactive first and then update this
        // account to active.
        ContentValues values = new ContentValues(1);
        values.put(Imps.Account.ACTIVE, 0);
        ContentResolver cr = getContentResolver();
        cr.update(Imps.Account.CONTENT_URI, values,
                Imps.Account.PROVIDER + "=" + providerId, null);

        values.put(Imps.Account.ACTIVE, 1);
        cr.update(ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId),
                values, null, null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        if (pbarDialog != null)   	
        	pbarDialog.dismiss();


        if (mApp != null) {
            mApp.removePendingCall(mHandler);
        }
        if (mConn != null) {
            try {
                if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                    log("unregisterConnectonListener");
                }
                mConn.unregisterConnectionListener(mListener);
            } catch (RemoteException e) {
                Log.w(ImApp.LOG_TAG, "<SigningInActivity> Connection disappeared!");
            }
        }
        // When background data is enabled, we don't want this activity in the backlist
        // so we always call finish() when we leave signing in screen. Otherwise, we
        // don't finish since we need to keep signing in if user choose to enable background.
        if (mApp.isBackgroundDataEnabled()) {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, ID_CANCEL_SIGNIN, 0, R.string.menu_cancel_signin)
            .setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == ID_CANCEL_SIGNIN) {
            if (mConn != null) {
                try {
                    if (mConn.getState() == ImConnection.LOGGING_IN) {
                        
                    	if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                            log("Cancelling sign in");
                        }
                        
                    	mConn.cancelLogin();
                    
                        finish();
                    }
                } catch (RemoteException e) {
                    Log.w(ImApp.LOG_TAG, "<SigningInActivity> Connection disappeared!");
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Popup a dialog to ask the user whether he/she wants to enable
     * background connection to continue. If yes, enable the setting
     * and broadcast the change. Otherwise, quit the signing in window
     * immediately.
     */
    private void promptForBackgroundDataSetting() {
        new AlertDialog.Builder(SigningInActivity.this)
            .setTitle(R.string.bg_data_prompt_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(getString(R.string.bg_data_prompt_message, mProviderName))
            .setPositiveButton(R.string.bg_data_prompt_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Intent intent = new Intent(SYNC_SETTINGS_ACTION);
                    intent.addCategory(SYNC_SETTINGS_CATEGORY);
                    startActivity(intent);
                }
             })
            .setNegativeButton(R.string.bg_data_prompt_cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
             })
            .show();
    }

    void handleConnectionEvent(int state, ImErrorInfo error) {
        if (isFinishing()) {
            return;
        }

        if (state == ImConnection.LOGGED_IN) {

            if (pbarDialog != null)   	
            	pbarDialog.dismiss();
            
            // sign in successfully, finish and switch to contact list
            finish();
            try {
                Intent intent;
                long accountId = mConn.getAccountId();

                if (mToAddress != null) {
                    IChatSessionManager manager = mConn.getChatSessionManager();
                    IChatSession session = manager.getChatSession(mToAddress);
                    if(session == null) {
                        session = manager.createChatSession(mToAddress);
                    }
                    Uri data = ContentUris.withAppendedId(Imps.Chats.CONTENT_URI, session.getId());
                    intent = new Intent(Intent.ACTION_VIEW, data);
                    intent.putExtra("from", mToAddress);
                    intent.putExtra("providerId", mProviderId);
                    intent.putExtra("accountId", accountId);
                    intent.addCategory(ImApp.IMPS_CATEGORY);

                } else {
                    intent = new Intent(this, ContactListActivity.class);
                    intent.putExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID, accountId);
                    
                }
                startActivity(intent);
            } catch (RemoteException e) {
                // Ouch!  Service died!  We'll just disappear.
                Log.w(ImApp.LOG_TAG, "<SigningInActivity> Connection disappeared while signing in!");
            }
        } else if (state == ImConnection.DISCONNECTED) {
        	

            if (pbarDialog != null)   	
            	pbarDialog.dismiss();
            
            // sign in failed
            Resources r = getResources();
            new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(r.getString(R.string.login_service_failed, mProviderName,
                            error == null? "": ErrorResUtils.getErrorRes(r, error.getCode())))
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                setResult(RESULT_CANCELED);
                                finish();
                            }
                        })
                .setCancelable(false)
                .show();
        }
    }

    private static final void log(String msg) {
        Log.d(ImApp.LOG_TAG, "<SigningInActivity>" + msg);
    }

    private final class MyConnectionListener extends ConnectionListenerAdapter {
        MyConnectionListener(Handler handler) {
            super(handler);
        }

        @Override
        public void onConnectionStateChange(IImConnection connection,
                int state, ImErrorInfo error) {
            handleConnectionEvent(state, error);
        }
    }
}
