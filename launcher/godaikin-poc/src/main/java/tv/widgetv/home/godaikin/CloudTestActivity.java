package tv.widgetv.home.godaikin;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Temporary remote-friendly developer harness. No automation extras, secrets in intents or AC controls. */
public final class CloudTestActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private CloudCredentialVault vault;
    private EditText user,password;
    private TextView result;
    private Button test,forget;
    private volatile boolean stopped;
    private GoDaikinCloudClient client;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        vault=new CloudCredentialVault(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60,28,60,28);root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.setBackgroundColor(0xff081728);scroll.addView(root);setContentView(scroll);
        TextView heading=new TextView(this);heading.setTextSize(25);heading.setText("GO DAIKIN — isolated READ-ONLY cloud test");root.addView(heading);
        TextView note=new TextView(this);note.setText("Unofficial Malaysia cloud protocol. Enter credentials privately on this TV.\nOnly login and registered-device/status reads. No AC control commands exist in this test.\nOctahedron v2.0 is unchanged. BACK returns to the launcher.");note.setTextSize(16);root.addView(note);
        user=new EditText(this);user.setHint("GO DAIKIN email");user.setSingleLine(true);user.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);user.setSaveEnabled(false);root.addView(user);
        password=new EditText(this);password.setHint("GO DAIKIN password");password.setSingleLine(true);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSaveEnabled(false);root.addView(password);
        test=new Button(this);test.setText(vault.hasAccount()?"Test saved account — reads only":"Save securely and test — reads only");root.addView(test);
        forget=new Button(this);forget.setText("Forget GO DAIKIN test credentials");root.addView(forget);
        result=new TextView(this);result.setTextSize(16);result.setText("Not tested. No cloud requests sent.");root.addView(result);
        test.setOnClickListener(v->startTest());
        forget.setOnClickListener(v->{busy(true);worker.execute(()->{String text;try{vault.clear();deleteFile("read-only-result.txt");text="GO DAIKIN test credentials and result removed.";}catch(Exception e){text="Secure removal failed.";}String message=text;runOnUiThread(()->{if(!stopped){result.setText(message);test.setText("Save securely and test — reads only");busy(false);}});});});
        if(vault.hasAccount())test.requestFocus();else user.requestFocus();
    }

    private void busy(boolean value){test.setEnabled(!value);forget.setEnabled(!value);user.setEnabled(!value);password.setEnabled(!value);}
    private void startTest(){
        final String enteredUser=user.getText().toString().trim(),enteredPassword=password.getText().toString();
        if((!enteredUser.isEmpty()||!enteredPassword.isEmpty())&&(enteredUser.isEmpty()||enteredPassword.isEmpty())){result.setText("Enter both email and password, or leave both blank to use the saved account.");return;}
        if(enteredUser.isEmpty()&&!vault.hasAccount()){result.setText("Enter your GO DAIKIN email and password on the TV.");return;}
        user.setText("");password.setText("");busy(true);result.setText("Testing login, registered-device discovery and reported status. No controls.");
        deleteFile("read-only-result.txt");
        worker.execute(()->{
            String message;
            try{
                if(!enteredUser.isEmpty())vault.save(enteredUser,enteredPassword);
                CloudCredentialVault.Account account=vault.load();
                if(account==null)throw new IllegalStateException();
                try(GoDaikinCloudClient cloud=new GoDaikinCloudClient(new CloudHttpsTransport(),SystemClock::elapsedRealtime)){
                    client=cloud;cloud.authenticate(account.user,account.password);
                    GoDaikinCloudClient.Result data=cloud.readRegisteredDevices();
                    message=data.summary();
                }finally{client=null;}
            }catch(GoDaikinCloudClient.CloudException e){message="REAL CLOUD TEST: "+e.reason.name()+". No AC control commands sent.\n"+help(e.reason);}
            catch(Exception e){message="TEST SETUP FAILED: secure storage or local test error. No AC control commands sent.";}
            // Sanitized whitelist summary only: never raw bodies, account name, device keys or tokens.
            if(!stopped){try(java.io.FileOutputStream out=openFileOutput("read-only-result.txt",MODE_PRIVATE)){out.write(message.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
            final String shown=message;
            runOnUiThread(()->{if(!stopped){result.setText(shown);test.setText("Test saved account — reads only");busy(false);test.requestFocus();}});
        });
    }
    private static String help(GoDaikinCloudClient.Failure f){
        switch(f){
            case AUTH_REJECTED:return "Login was rejected. Check the Malaysia GO DAIKIN email/password and account activation privately. This can also mean a different regional account pool.";
            case CHALLENGE_REQUIRED:return "Account requires MFA, password change or another challenge. This proof of concept stops; use the official app.";
            case AUTH_FLOW_UNSUPPORTED:return "The reference password-login flow was rejected by Cognito. No alternative or bypass attempted.";
            case API_REJECTED:return "Device-list API rejected the request. Subscription, regional API or protocol compatibility needs investigation.";
            case RATE_LIMITED:return "Cloud rate limit reached. No automatic retry; wait before testing again.";
            case NETWORK:return "Could not complete the cloud request. Check TV internet connectivity; no automatic retry.";
            case TLS:return "TLS validation failed. Check TV date/time and internet; certificate verification remains enabled.";
            case MALFORMED_RESPONSE:return "Unexpected cloud response. Compatibility is unverified; raw data was not logged.";
            default:return "Cloud compatibility has not been verified.";
        }
    }
    @Override protected void onStop(){super.onStop();user.setText("");password.setText("");}
    @Override protected void onDestroy(){stopped=true;worker.shutdownNow();super.onDestroy();}
}
