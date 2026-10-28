package tv.widgetv.home;

import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/** Current TP-Link TDP discovery envelope, independent of authenticated device sessions. */
final class TpLinkDiscoveryCrypto {
    private final KeyPair key;
    TpLinkDiscoveryCrypto()throws Exception{KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);key=generator.generateKeyPair();}
    byte[] query()throws Exception{
        String pem="-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(key.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n";
        byte[] payload=TpLinkCrypto.bytes(new JSONObject().put("params",new JSONObject().put("rsa_key",pem)).toString());
        ByteBuffer frame=ByteBuffer.allocate(16+payload.length).order(ByteOrder.BIG_ENDIAN);
        frame.put((byte)2).put((byte)0).putShort((short)1).putShort((short)payload.length).put((byte)17).put((byte)0).putInt(new SecureRandom().nextInt()).putInt(0x5a6b7c8d).put(payload);
        CRC32 crc=new CRC32();crc.update(frame.array());frame.putInt(12,(int)crc.getValue());return frame.array();
    }
    JSONObject unwrap(JSONObject message)throws Exception{
        JSONObject result=message.optJSONObject("result");if(result==null)return message;JSONObject encrypted=result.optJSONObject("encrypt_info");if(encrypted==null||!encrypted.has("key")||!encrypted.has("data"))return message;
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");rsa.init(Cipher.DECRYPT_MODE,key.getPrivate(),new OAEPParameterSpec("SHA-1","MGF1",MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT));
        byte[] secret=rsa.doFinal(Base64.getDecoder().decode(encrypted.getString("key")));if(secret.length!=32)throw new java.io.IOException("Invalid discovery key");
        byte[] plain=TpLinkCrypto.aes(false,Arrays.copyOf(secret,16),Arrays.copyOfRange(secret,16,32),Base64.getDecoder().decode(encrypted.getString("data")));Arrays.fill(secret,(byte)0);
        JSONObject decoded=new JSONObject(new String(plain,java.nio.charset.StandardCharsets.UTF_8));java.util.Iterator<String> keys=decoded.keys();while(keys.hasNext()){String name=keys.next();if(!result.has(name)||result.isNull(name))result.put(name,decoded.get(name));}Arrays.fill(plain,(byte)0);return message;
    }
}
