package tv.widgetv.home;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Independently implemented wire primitives, using JCA. See docs/SMART-HOME-PROTOCOLS.md. */
final class TpLinkCrypto {
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static byte[] join(byte[]...parts){int n=0;for(byte[] p:parts)n+=p.length;byte[] out=new byte[n];int offset=0;for(byte[] p:parts){System.arraycopy(p,0,out,offset,p.length);offset+=p.length;}return out;}
    static byte[] hash(String algorithm,byte[]...parts) throws Exception{return MessageDigest.getInstance(algorithm).digest(join(parts));}
    static byte[] sha(byte[]...parts)throws Exception{return hash("SHA-256",parts);}
    static byte[] auth(String user,String pass,int version)throws Exception{
        return version==1?hash("MD5",hash("MD5",bytes(user)),hash("MD5",bytes(pass))):sha(hash("SHA-1",bytes(user)),hash("SHA-1",bytes(pass)));
    }
    static byte[] proof(byte[] local,byte[] remote,byte[] auth,int version,boolean second)throws Exception{
        return version==1?sha(second?remote:local,auth):second?sha(remote,local,auth):sha(local,remote,auth);
    }
    static byte[] aes(boolean encrypt,byte[] key,byte[] iv,byte[] data)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));return cipher.doFinal(data);
    }
    static byte[] xor(byte[] plain,boolean decode){byte[] out=new byte[plain.length];int key=171;for(int i=0;i<plain.length;i++){int next=plain[i]&255;out[i]=(byte)(next^key);key=decode?next:(out[i]&255);}return out;}
    static String hex(byte[] input){StringBuilder s=new StringBuilder();for(byte b:input)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    static final class Klap {
        final byte[] key,ivPrefix,signatureKey;int sequence;
        Klap(byte[] local,byte[] remote,byte[] auth)throws Exception{
            key=Arrays.copyOf(sha(bytes("lsk"),local,remote,auth),16);
            byte[] iv=sha(bytes("iv"),local,remote,auth);ivPrefix=Arrays.copyOf(iv,12);sequence=ByteBuffer.wrap(iv,28,4).getInt();
            signatureKey=Arrays.copyOf(sha(bytes("ldk"),local,remote,auth),28);
        }
        byte[] seq(){return ByteBuffer.allocate(4).putInt(sequence).array();}
        byte[] encrypt(byte[] input)throws Exception{sequence++;return encodeAtCurrentSequence(input);}
        byte[] encodeAtCurrentSequence(byte[] input)throws Exception{
            byte[] cipher=aes(true,key,join(ivPrefix,seq()),input);return join(sha(signatureKey,seq(),cipher),cipher);
        }
        byte[] decrypt(byte[] input)throws Exception{
            if(input.length<48||(input.length-32)%16!=0)throw new java.io.IOException("Invalid encrypted frame");
            byte[] cipher=Arrays.copyOfRange(input,32,input.length);
            if(!MessageDigest.isEqual(Arrays.copyOf(input,32),sha(signatureKey,seq(),cipher)))throw new java.io.IOException("Encrypted response authentication failed");
            return aes(false,key,join(ivPrefix,seq()),cipher);
        }
    }
}
