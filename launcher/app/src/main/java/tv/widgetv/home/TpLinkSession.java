package tv.widgetv.home;

import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;

/** One device/session at a time. No credentials, seeds or tokens are logged or persisted. */
final class TpLinkSession {
    interface Http {byte[] post(String path,byte[] data,boolean json)throws Exception;}
    static final class ProtocolException extends IOException{ProtocolException(String safe){super(safe);}}
    private final Http http;private final String username,password,protocol;
    private TpLinkCrypto.Klap klap;private byte[] aesKey,aesIv;private String token;
    private long established;
    TpLinkSession(Http http,String user,String password,String protocol){this.http=http;this.username=user;this.password=password;this.protocol=protocol;}
    static JSONObject check(JSONObject j)throws Exception{
        if(j.optInt("error_code",0)!=0){int code=j.optInt("error_code");throw new ProtocolException(code==-1501||code==1111||code==-20651||code==9999?"Authentication/session failed. Check TP-Link credentials and third-party compatibility.":"Device rejected request (code "+code+")");}return j;
    }
    synchronized JSONObject request(JSONObject data)throws Exception{
        if(System.nanoTime()-established>1_800_000_000_000L){klap=null;aesKey=null;token=null;}
        try{
            if(klap==null&&aesKey==null)establish();
            JSONObject response;
            if(klap!=null){byte[] payload=klap.encrypt(TpLinkCrypto.bytes(data.toString()));response=new JSONObject(new String(klap.decrypt(http.post("/app/request?seq="+klap.sequence,payload,false)),StandardCharsets.UTF_8));}
            else response=aesSend(data);
            return check(response);
        }catch(Exception e){klap=null;aesKey=null;token=null;throw e;}
    }
    private void establish()throws Exception{
        if(username==null||password==null||username.isEmpty()||password.isEmpty())throw new ProtocolException("TP-Link account credentials required. Enter them privately in Manage Devices.");
        if(protocol.startsWith("AES")){establishAes(protocol.equals("AES2")?2:1);return;}
        byte[] local=new byte[16];new SecureRandom().nextBytes(local);byte[] reply;
        try{reply=http.post("/app/handshake1",local,false);}catch(Exception e){if(!protocol.equals("AUTO"))throw e;establishAes(1);return;}
        if(reply.length!=48){if(protocol.equals("AUTO")){establishAes(1);return;}throw new ProtocolException("Unsupported KLAP handshake. Check advertised firmware protocol.");}
        byte[] remote=Arrays.copyOf(reply,16),auth=null;int selected=0;
        for(int v:new int[]{2,1}){if(protocol.startsWith("KLAP1")&&v!=1||protocol.startsWith("KLAP2")&&v!=2)continue;
            byte[] candidate=TpLinkCrypto.auth(username,password,v);if(MessageDigest.isEqual(Arrays.copyOfRange(reply,16,48),TpLinkCrypto.proof(local,remote,candidate,v,false))){auth=candidate;selected=v;break;}}
        if(auth==null)throw new ProtocolException("Authentication failed. Check case-sensitive TP-Link credentials and third-party compatibility. No fallback to demo.");
        http.post("/app/handshake2",TpLinkCrypto.proof(local,remote,auth,selected,true),false);klap=new TpLinkCrypto.Klap(local,remote,auth);established=System.nanoTime();
    }
    private void establishAes(int loginVersion)throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(1024);KeyPair key=generator.generateKeyPair();
        String pub="-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(key.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n";
        JSONObject handshake=check(new JSONObject(new String(http.post("/app",TpLinkCrypto.bytes(new JSONObject().put("method","handshake").put("params",new JSONObject().put("key",pub)).toString()),true),StandardCharsets.UTF_8)));
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,key.getPrivate());byte[] secret=rsa.doFinal(Base64.getDecoder().decode(handshake.getJSONObject("result").getString("key")));
        if(secret.length!=32)throw new ProtocolException("Unsupported AES handshake key");aesKey=Arrays.copyOf(secret,16);aesIv=Arrays.copyOfRange(secret,16,32);Arrays.fill(secret,(byte)0);
        String userHash=TpLinkCrypto.hex(TpLinkCrypto.hash("SHA-1",TpLinkCrypto.bytes(username)));
        String passwordValue=loginVersion==2?TpLinkCrypto.hex(TpLinkCrypto.hash("SHA-1",TpLinkCrypto.bytes(password))):password;
        JSONObject params=new JSONObject().put("username",Base64.getEncoder().encodeToString(TpLinkCrypto.bytes(userHash)))
            .put(loginVersion==2?"password2":"password",Base64.getEncoder().encodeToString(TpLinkCrypto.bytes(passwordValue)));
        JSONObject login=check(aesSend(new JSONObject().put("method","login_device").put("params",params).put("request_time_milis",System.currentTimeMillis())));
        token=login.getJSONObject("result").getString("token");established=System.nanoTime();
    }
    private JSONObject aesSend(JSONObject request)throws Exception{
        String encrypted=Base64.getEncoder().encodeToString(TpLinkCrypto.aes(true,aesKey,aesIv,TpLinkCrypto.bytes(request.toString())));
        JSONObject frame=new JSONObject().put("method","securePassthrough").put("params",new JSONObject().put("request",encrypted));
        String path="/app"+(token==null?"":"?token="+java.net.URLEncoder.encode(token,"UTF-8"));
        JSONObject response=check(new JSONObject(new String(http.post(path,TpLinkCrypto.bytes(frame.toString()),true),StandardCharsets.UTF_8)));
        byte[] decrypted=TpLinkCrypto.aes(false,aesKey,aesIv,Base64.getDecoder().decode(response.getJSONObject("result").getString("response")));
        return new JSONObject(new String(decrypted,StandardCharsets.UTF_8));
    }
}
