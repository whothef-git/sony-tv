package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

/** Fixed HTTPS destinations, standard platform TLS/hostname validation, no redirects or logging. */
public final class CloudHttpsTransport implements GoDaikinCloudClient.Transport {
    private static final int MAX_BYTES=1024*1024;
    interface Gate { boolean allow(String url,Map<String,String> headers,JSONObject body); }
    private final Gate commands;
    public CloudHttpsTransport(){commands=null;}
    CloudHttpsTransport(Gate commands){this.commands=commands;}

    static boolean allowed(String url,Map<String,String> headers,JSONObject body){
        if(GoDaikinCloudClient.LIST_URL.equals(url)){
            JSONObject d=body.optJSONObject("requestData");
            return body.length()==1&&d!=null&&d.length()==2&&Integer.valueOf(1).equals(d.opt("type"))
                &&d.opt("value") instanceof String&&!d.optString("value").isEmpty()
                &&headers.containsKey("authorization");
        }
        if(GoDaikinCloudClient.AUTH_URL.equals(url)){
            JSONObject p=body.optJSONObject("AuthParameters");String flow=body.optString("AuthFlow");
            if(body.length()!=3||!GoDaikinCloudClient.CLIENT_ID.equals(body.optString("ClientId"))||p==null
                ||!GoDaikinCloudClient.AUTH_TARGET.equals(headers.get("X-Amz-Target")))return false;
            if("USER_PASSWORD_AUTH".equals(flow))return p.length()==2&&p.opt("USERNAME") instanceof String&&p.opt("PASSWORD") instanceof String;
            if("REFRESH_TOKEN_AUTH".equals(flow))return p.length()==1&&p.opt("REFRESH_TOKEN") instanceof String;
        }
        return false;
    }

    @Override public GoDaikinCloudClient.Response post(String url,Map<String,String> headers,JSONObject body)
        throws IOException,GoDaikinCloudClient.CloudException {
        if(!allowed(url,headers,body)&&(commands==null||!commands.allow(url,headers,body)))
            throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.CONFIRMATION_REQUIRED);
        HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();
        byte[] request=body.toString().getBytes(StandardCharsets.UTF_8);
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setUseCaches(false);connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(request.length);
            for(Map.Entry<String,String> h:headers.entrySet())connection.setRequestProperty(h.getKey(),h.getValue());
            try(OutputStream output=connection.getOutputStream()){output.write(request);}
            int status=connection.getResponseCode();
            InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
            JSONObject json=new JSONObject();
            if(stream!=null)try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException();
                    if(output.size()+n>MAX_BYTES)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE);
                    output.write(buffer,0,n);
                }
                byte[] raw=output.toByteArray();
                try{json=new JSONObject(new String(raw,StandardCharsets.UTF_8));}
                catch(org.json.JSONException e){if(status==200)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE);}
                finally{Arrays.fill(raw,(byte)0);Arrays.fill(buffer,(byte)0);output.reset();}
            }
            return new GoDaikinCloudClient.Response(status,json);
        }finally{Arrays.fill(request,(byte)0);connection.disconnect();}
    }
}
