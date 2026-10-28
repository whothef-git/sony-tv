package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Android Keystore key never leaves the TV. Passwords/tokens never enter ordinary metadata. */
final class SmartCredentialStore {
    static final String ACCOUNT="tp-link-account";
    static final class Credentials{final String username,password;Credentials(String u,String p){username=u;password=p;}}
    private static final String ALIAS="octahedron.smart-home.v2";
    private final SharedPreferences encrypted;
    SmartCredentialStore(Context c){encrypted=c.getSharedPreferences("smart_credentials_encrypted_v2",Context.MODE_PRIVATE);}
    boolean hasAccount(){return encrypted.contains(ACCOUNT);}
    private SecretKey key() throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generator.generateKey();}
        return (SecretKey)store.getKey(ALIAS,null);
    }
    synchronized void save(String id,String user,String password) throws Exception{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD(id.getBytes(StandardCharsets.UTF_8));
        byte[] raw=new JSONObject().put("username",user).put("password",password).toString().getBytes(StandardCharsets.UTF_8);
        byte[] data=c.doFinal(raw);java.util.Arrays.fill(raw,(byte)0);
        String value=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(data,Base64.NO_WRAP);
        if(!encrypted.edit().putString(id,value).commit())throw new java.io.IOException("Credentials could not be saved securely");
    }
    synchronized Credentials load(String id) throws Exception{
        String value=encrypted.getString(id,null);if(value==null&&!id.equals(ACCOUNT))value=encrypted.getString(ACCOUNT,null);
        String aad=encrypted.contains(id)?id:ACCOUNT;if(value==null)return null;String[] parts=value.split(":",2);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] plain=c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP));try{JSONObject j=new JSONObject(new String(plain,StandardCharsets.UTF_8));return new Credentials(j.getString("username"),j.getString("password"));}finally{java.util.Arrays.fill(plain,(byte)0);}
    }
    synchronized void remove(String id) throws Exception{if(!encrypted.edit().remove(id).commit())throw new java.io.IOException("Credentials could not be removed");}
}
