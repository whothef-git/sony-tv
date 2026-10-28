package tv.widgetv.home.godaikin;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Mock protocol + actual TV Keystore test. Never accesses production or user test credentials. */
public final class CloudMockChecks extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle out=new Bundle();String prefix="mock-"+java.util.UUID.randomUUID();
        Context isolated=new ContextWrapper(getTargetContext()){
            @Override public SharedPreferences getSharedPreferences(String n,int m){return super.getSharedPreferences(prefix+n,m);}
        };
        CloudCredentialVault vault=new CloudCredentialVault(isolated,prefix);
        try{
            int count=new CloudClientChecks().run()+new TemperatureChecks().run()+new ControlChecks().run();
            vault.save("synthetic@example.invalid","synthetic-only-password");
            if(!vault.hasAccount()||!vault.load().password.equals("synthetic-only-password"))throw new AssertionError("Keystore round trip");count++;
            String stored=isolated.getSharedPreferences("godaikin_poc_encrypted",0).getAll().toString();
            if(stored.contains("synthetic@example")||stored.contains("synthetic-only-password"))throw new AssertionError("plaintext stored");count++;
            CloudCredentialVault reopened=new CloudCredentialVault(isolated,prefix);
            if(!reopened.load().user.equals("synthetic@example.invalid"))throw new AssertionError("storage reopen");count++;
            vault.saveControl("binding",new org.json.JSONObject().put("id","synthetic-private-cloud-id"));
            vault.saveControl("journal",new org.json.JSONObject().put("phase","CHANGE_ATTEMPT_PENDING").put("original",new org.json.JSONObject().put("temp",23)));
            if(!reopened.loadControl("binding").getString("id").equals("synthetic-private-cloud-id"))throw new AssertionError("encrypted binding");count++;
            if(reopened.loadControl("journal").getJSONObject("original").getInt("temp")!=23)throw new AssertionError("encrypted original journal");count++;
            stored=isolated.getSharedPreferences("godaikin_poc_encrypted",0).getAll().toString();
            if(stored.contains("synthetic-private-cloud-id")||stored.contains("CHANGE_ATTEMPT_PENDING"))throw new AssertionError("plaintext control metadata");count++;
            if(!reopened.load().password.equals("synthetic-only-password"))throw new AssertionError("control storage changed credentials");count++;
            vault.clear();if(vault.hasAccount()||vault.load()!=null)throw new AssertionError("remove credentials");count++;
            out.putString("stream","PASS: "+count+" mock cloud + real Android Keystore checks. No GO DAIKIN cloud or AC commands.\n");finish(Activity.RESULT_OK,out);
        }catch(Throwable e){out.putString("stream","FAIL: "+e.getClass().getSimpleName()+" (sensitive exception text omitted)\n");finish(Activity.RESULT_CANCELED,out);}
        finally{try{vault.clear();}catch(Exception ignored){}getTargetContext().deleteSharedPreferences(prefix+"godaikin_poc_encrypted");}
    }
}
