package tv.widgetv.home.godaikin;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Separate application sandbox/key; cannot read or overwrite TP-Link credentials. Tokens stay in RAM. */
final class CloudCredentialVault {
    private final String alias;
    private static final byte[] AAD="godaikin-account-v1".getBytes(StandardCharsets.UTF_8);
    private final SharedPreferences prefs;
    static final class Account { final String user,password;Account(String u,String p){user=u;password=p;} }
    CloudCredentialVault(Context context){this(context,"octahedron.godaikin.poc.v1");}
    CloudCredentialVault(Context context,String alias){this.alias=alias;prefs=context.getSharedPreferences("godaikin_poc_encrypted",Context.MODE_PRIVATE);}
    boolean hasAccount(){return prefs.contains("account");}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)){
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());g.generateKey();
        }
        return (SecretKey)store.getKey(alias,null);
    }
    synchronized void save(String user,String password)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(AAD);
        byte[] plain=new JSONObject().put("user",user).put("password",password).toString().getBytes(StandardCharsets.UTF_8);
        try{
            String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(plain),Base64.NO_WRAP);
            if(!prefs.edit().putString("account",value).commit())throw new java.io.IOException();
        }finally{Arrays.fill(plain,(byte)0);}
    }
    synchronized Account load()throws Exception{
        String value=prefs.getString("account",null);if(value==null)return null;String[] parts=value.split(":",2);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(AAD);
        byte[] plain=cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP));
        try{JSONObject j=new JSONObject(new String(plain,StandardCharsets.UTF_8));return new Account(j.getString("user"),j.getString("password"));}
        finally{Arrays.fill(plain,(byte)0);}
    }
    synchronized void clear()throws Exception{
        if(!prefs.edit().clear().commit())throw new java.io.IOException();
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(alias))store.deleteEntry(alias);
    }
    synchronized void saveControl(String field,JSONObject value)throws Exception{
        if(!field.equals("binding")&&!field.equals("journal"))throw new IllegalArgumentException();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        cipher.updateAAD(("godaikin-control-v1:"+field).getBytes(StandardCharsets.UTF_8));
        byte[] plain=value.toString().getBytes(StandardCharsets.UTF_8);
        try{
            String encoded=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(plain),Base64.NO_WRAP);
            if(!prefs.edit().putString(field,encoded).commit())throw new java.io.IOException();
        }finally{Arrays.fill(plain,(byte)0);}
    }
    synchronized JSONObject loadControl(String field)throws Exception{
        if(!field.equals("binding")&&!field.equals("journal"))throw new IllegalArgumentException();
        String value=prefs.getString(field,null);if(value==null)return null;String[] parts=value.split(":",2);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        cipher.updateAAD(("godaikin-control-v1:"+field).getBytes(StandardCharsets.UTF_8));
        byte[] plain=cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP));
        try{return new JSONObject(new String(plain,StandardCharsets.UTF_8));}finally{Arrays.fill(plain,(byte)0);}
    }
}
