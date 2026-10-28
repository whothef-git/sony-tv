package tv.widgetv.home;

import org.json.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.nio.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Synthetic protocol peers, never real account/device data. Does not contact a network. */
public final class SmartProtocolChecks {
    static int count;
    static final String USER="test@example.invalid",PASS="not-a-real-password";
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);count++;}
    interface Checked{void run()throws Exception;}
    static void rejects(Checked run,String why)throws Exception{try{run.run();}catch(Exception expected){count++;return;}throw new AssertionError(why);}
    static byte[] b(String s){return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    static byte[] concat(byte[]...parts){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();for(byte[] p:parts)out.write(p,0,p.length);return out.toByteArray();}
    static byte[] sha(byte[]...p)throws Exception{return MessageDigest.getInstance("SHA-256").digest(concat(p));}
    static byte[] crypt(boolean encrypt,byte[] key,byte[] iv,byte[] data)throws Exception{Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(encrypt?1:2,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));return c.doFinal(data);}
    static JSONObject response(JSONObject request,Peer p)throws Exception{
        String method=request.getString("method");if(method.equals("set_device_info")){p.writes++;p.on=request.getJSONObject("params").getBoolean("device_on");return new JSONObject().put("error_code",0);}
        if(method.equals("get_energy_usage"))return new JSONObject().put("error_code",0).put("result",new JSONObject().put("current_power",p.on?58000:0).put("today_energy",1320));
        return new JSONObject().put("error_code",0).put("result",new JSONObject().put("model","P110").put("device_on",p.on).put("mac","AA-BB-CC-DD-EE-01").put("device_id","synthetic-device"));
    }
    static final class Peer implements TpLinkSession.Http {
        final int version;final boolean aes,wrongAuth;boolean on=true,tamper,timeout;int writes,handshakes,lastSeq;byte[] local,remote=new byte[16],auth,key,ivPrefix,sig,aesIv;boolean login;
        Peer(int version,boolean aes,boolean wrongAuth){this.version=version;this.aes=aes;this.wrongAuth=wrongAuth;for(int i=0;i<16;i++)remote[i]=(byte)(16+i);}
        public byte[] post(String path,byte[] data,boolean json)throws Exception{
            if(path.equals("/app/handshake1")){
                if(aes)throw new java.io.IOException("No KLAP transport");handshakes++;local=data.clone();String password=wrongAuth?"wrong-synthetic-password":PASS;
                auth=version==2?sha(MessageDigest.getInstance("SHA-1").digest(b(USER)),MessageDigest.getInstance("SHA-1").digest(b(password))):MessageDigest.getInstance("MD5").digest(concat(MessageDigest.getInstance("MD5").digest(b(USER)),MessageDigest.getInstance("MD5").digest(b(password))));
                byte[] proof=version==2?sha(local,remote,auth):sha(local,auth);return concat(remote,proof);
            }
            if(path.equals("/app/handshake2")){
                check(MessageDigest.isEqual(data,version==2?sha(remote,local,auth):sha(remote,auth)),"handshake2 proof");
                key=Arrays.copyOf(sha(b("lsk"),local,remote,auth),16);byte[] iv=sha(b("iv"),local,remote,auth);ivPrefix=Arrays.copyOf(iv,12);lastSeq=ByteBuffer.wrap(iv,28,4).getInt();sig=Arrays.copyOf(sha(b("ldk"),local,remote,auth),28);return new byte[0];
            }
            if(path.startsWith("/app/request?seq=")){
                int sequence=Integer.parseInt(path.substring(path.indexOf('=')+1));check(sequence==lastSeq+1,"monotonic KLAP sequence");lastSeq=sequence;byte[] seq=ByteBuffer.allocate(4).putInt(sequence).array(),cipher=Arrays.copyOfRange(data,32,data.length);
                check(MessageDigest.isEqual(Arrays.copyOf(data,32),sha(sig,seq,cipher)),"request signature");JSONObject request=new JSONObject(new String(crypt(false,key,concat(ivPrefix,seq),cipher),"UTF-8"));
                JSONObject result=response(request,this);if(timeout){timeout=false;throw new java.net.SocketTimeoutException();}
                byte[] reply=crypt(true,key,concat(ivPrefix,seq),b(result.toString()));byte[] encoded=concat(sha(sig,seq,reply),reply);if(tamper)encoded[0]^=1;return encoded;
            }
            JSONObject frame=new JSONObject(new String(data,"UTF-8"));
            if(frame.getString("method").equals("handshake")){
                handshakes++;String pem=frame.getJSONObject("params").getString("key").replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
                PublicKey publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));key=new byte[16];aesIv=new byte[16];Arrays.fill(key,(byte)7);Arrays.fill(aesIv,(byte)9);Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(1,publicKey);
                return b(new JSONObject().put("error_code",0).put("result",new JSONObject().put("key",Base64.getEncoder().encodeToString(rsa.doFinal(concat(key,aesIv))))).toString());
            }
            byte[] encrypted=Base64.getDecoder().decode(frame.getJSONObject("params").getString("request"));JSONObject request=new JSONObject(new String(crypt(false,key,aesIv,encrypted),"UTF-8"));JSONObject result;
            if(request.getString("method").equals("login_device")){
                JSONObject params=request.getJSONObject("params");String decoded=new String(Base64.getDecoder().decode(params.getString(version==2?"password2":"password")),"UTF-8");
                check(decoded.equals(version==2?TpLinkCrypto.hex(MessageDigest.getInstance("SHA-1").digest(b(PASS))):PASS),"AES login encoding");login=true;result=new JSONObject().put("error_code",wrongAuth?-1501:0).put("result",new JSONObject().put("token","synthetic-token"));
            }else{check(login&&path.equals("/app?token=synthetic-token"),"AES token scoped to app request");result=response(request,this);}
            return b(new JSONObject().put("error_code",0).put("result",new JSONObject().put("response",Base64.getEncoder().encodeToString(crypt(true,key,aesIv,b(result.toString()))))).toString());
        }
    }
    public static void main(String[] args)throws Exception{
        check(TpLinkCrypto.hex(TpLinkCrypto.auth(USER,PASS,1)).equals("d41d94f091361e86f7b779479f15d88d"),"independent KLAP1 vector");
        check(TpLinkCrypto.hex(TpLinkCrypto.auth(USER,PASS,2)).equals("fdb51952f84867dc0f1db6ba4946711c7cfc2b8eaa26826db57e50ec7ea1a39a"),"independent KLAP2 vector");
        byte[] local=new byte[16],remote=new byte[16];for(int i=0;i<16;i++){local[i]=(byte)i;remote[i]=(byte)(16+i);}
        check(TpLinkCrypto.hex(TpLinkCrypto.proof(local,remote,TpLinkCrypto.auth(USER,PASS,2),2,false)).equals("de73d93714ff683a5ad75ebd567fcccd95e7f8d77f0f214eb28a8e66e88d5941"),"independent challenge vector");
        TpLinkCrypto.Klap vector=new TpLinkCrypto.Klap(local,remote,TpLinkCrypto.auth(USER,PASS,2));check(TpLinkCrypto.hex(vector.key).equals("22b67b9e2c80e261142986303f22dc88"),"independent key vector");
        byte[] plain=b("{\"system\":{\"get_sysinfo\":{}}}");check(Arrays.equals(plain,TpLinkCrypto.xor(TpLinkCrypto.xor(plain,false),true)),"XOR framing round trip");
        for(int version:new int[]{1,2}){Peer peer=new Peer(version,false,false);TpLinkSession session=new TpLinkSession(peer,USER,PASS,"AUTO");JSONObject state=session.request(new JSONObject().put("method","get_device_info"));check(state.getJSONObject("result").getBoolean("device_on"),"KLAP online ON");session.request(new JSONObject().put("method","set_device_info").put("params",new JSONObject().put("device_on",false)));check(!session.request(new JSONObject().put("method","get_device_info")).getJSONObject("result").getBoolean("device_on"),"KLAP confirmed OFF");check(peer.handshakes==1,"session reused");peer.tamper=true;rejects(()->session.request(new JSONObject().put("method","get_device_info")),"tampered response rejected");}
        rejects(()->new TpLinkSession(new Peer(2,false,true),USER,PASS,"KLAP2").request(new JSONObject().put("method","get_device_info")),"invalid KLAP credentials");
        for(int version:new int[]{1,2}){Peer peer=new Peer(version,true,false);TpLinkSession session=new TpLinkSession(peer,USER,PASS,"AES"+version);check(session.request(new JSONObject().put("method","get_device_info")).getJSONObject("result").getBoolean("device_on"),"AES authenticated state");}
        rejects(()->new TpLinkSession(new Peer(1,true,true),USER,PASS,"AES1").request(new JSONObject().put("method","get_device_info")),"invalid AES credentials");
        Peer timeout=new Peer(2,false,false);TpLinkSession timed=new TpLinkSession(timeout,USER,PASS,"KLAP2");timed.request(new JSONObject().put("method","get_device_info"));timeout.timeout=true;
        rejects(()->timed.request(new JSONObject().put("method","set_device_info").put("params",new JSONObject().put("device_on",false))),"command timeout surfaced");check(timeout.writes==1,"no automatic command retry");check(!timed.request(new JSONObject().put("method","get_device_info")).getJSONObject("result").getBoolean("device_on"),"reconnect reads actual state after timeout");
        SmartDevice plug=new SmartDevice("saved-id",DeviceType.TAPO_P110,0,"Fan");plug.mac="AA-BB-CC-DD-EE-01";JSONObject info=new JSONObject().put("model","P110").put("mac","aabbccddee01").put("device_id","synthetic").put("device_on",false);
        SmartDevice actual=TpLinkState.parse(plug,info);check(actual.connection==ConnectionState.ONLINE&&actual.power==PowerState.OFF,"online OFF independent");check(actual.id.equals("saved-id"),"stable saved ID");
        TpLinkState.energy(actual,new JSONObject().put("current_power",58000).put("today_energy",1320));check(actual.watts==58&&actual.dailyKwh==1.32,"mW and Wh conversion");TpLinkState.energy(actual,new JSONObject());check(Double.isNaN(actual.watts)&&Double.isNaN(actual.dailyKwh),"missing readings unknown");
        rejects(()->TpLinkState.parse(plug,new JSONObject(info.toString()).put("mac","aabbccddee99")),"wrong device at old IP rejected");
        rejects(()->TpLinkState.parse(plug,new JSONObject().put("model","P110").put("device_on",false)),"unidentified device rejected");
        SmartDevice moved=plug.copy();moved.ip="192.168.50.99";check(TpLinkState.parse(moved,info).id.equals(plug.id),"same device at new IP preserves ID");
        SmartDevice strip=new SmartDevice("strip",DeviceType.KASA_KP303,1,"Strip");JSONObject stripInfo=new JSONObject().put("model","KP303").put("deviceId","strip-id");JSONArray children=new JSONArray();for(int i=0;i<3;i++)children.put(new JSONObject().put("id","actual-outlet-"+i).put("state",i%2));stripInfo.put("children",children);SmartDevice initial=TpLinkState.parse(strip,stripInfo);
        check(initial.outletIds[1].equals("actual-outlet-1")&&initial.outlets[1]==PowerState.ON,"actual outlet IDs");JSONArray reversed=new JSONArray().put(children.get(2)).put(children.get(0)).put(children.get(1));SmartDevice reordered=TpLinkState.parse(initial,new JSONObject(stripInfo.toString()).put("children",reversed));check(reordered.outletIds[0].equals("actual-outlet-0")&&reordered.outlets[1]==PowerState.ON,"outlet order stable after response reordering");
        for(int i=0;i<3;i++){JSONObject request=TpLinkState.iotRequest("set_relay_state",new JSONObject().put("state",1),initial.outletIds[i]);check(request.getJSONObject("context").getJSONArray("child_ids").getString(0).equals("actual-outlet-"+i),"independent outlet targeting "+i);}
        check(Double.isNaN(initial.watts)&&Double.isNaN(initial.dailyKwh),"KP303 no fabricated monitoring");
        rejects(()->TpLinkState.parse(strip,new JSONObject(stripInfo.toString()).put("children",new JSONArray().put(children.get(0)))) ,"incomplete outlet set rejected");
        JSONArray duplicate=new JSONArray().put(children.get(0)).put(children.get(0)).put(children.get(2));rejects(()->TpLinkState.parse(strip,new JSONObject(stripInfo.toString()).put("children",duplicate)),"duplicate outlet IDs rejected");
        TpLinkDiscoveryCrypto discovery=new TpLinkDiscoveryCrypto();byte[] query=discovery.query();ByteBuffer header=ByteBuffer.wrap(query);check((query[0]&255)==2&&header.getShort(4)==query.length-16,"TDP header length");
        int checksum=header.getInt(12);byte[] crcInput=query.clone();ByteBuffer.wrap(crcInput).putInt(12,0x5a6b7c8d);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(crcInput);check(checksum==(int)crc.getValue(),"TDP CRC");
        JSONObject queryJson=new JSONObject(new String(Arrays.copyOfRange(query,16,query.length),"UTF-8"));String pem=queryJson.getJSONObject("params").getString("rsa_key").replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
        PublicKey publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));byte[] secret=new byte[32];Arrays.fill(secret,(byte)5);Cipher oaep=Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");oaep.init(1,publicKey,new OAEPParameterSpec("SHA-1","MGF1",java.security.spec.MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT));
        JSONObject envelope=new JSONObject().put("result",new JSONObject().put("device_model","P110").put("encrypt_info",new JSONObject().put("key",Base64.getEncoder().encodeToString(oaep.doFinal(secret))).put("data",Base64.getEncoder().encodeToString(crypt(true,Arrays.copyOf(secret,16),Arrays.copyOfRange(secret,16,32),b("{\"mac\":\"aabbccddee01\",\"device_id\":\"mock-discovery\"}"))))));
        check(discovery.unwrap(envelope).getJSONObject("result").getString("mac").equals("aabbccddee01"),"encrypted discovery unwrap");
        check(discovery.unwrap(new JSONObject().put("result",new JSONObject().put("mac","plain"))).getJSONObject("result").getString("mac").equals("plain"),"plaintext discovery preserved");
        System.out.println("PASS: "+count+" synthetic protocol/state checks. No physical devices tested.");
    }
}
