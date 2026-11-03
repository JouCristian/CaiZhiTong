package cn.caizhitong.service;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import cn.caizhitong.config.AttachmentStorageConfiguration;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupabaseAttachmentStorageTest {
    private HttpClient client;
    private SupabaseAttachmentStorage storage;
    private final String object=UUID.randomUUID()+".png";
    @BeforeEach void setup() {
        client=mock(HttpClient.class);
        storage=new SupabaseAttachmentStorage("https://cloud-test.supabase.co","caizhitong-attachments","unit-test-storage-key",client);
    }
    @SuppressWarnings("unchecked")
    private void respond(int status,byte[] data) throws Exception {
        HttpResponse<InputStream> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenAnswer(i->new ByteArrayInputStream(data));
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    private void respond(int status,String body)throws Exception {respond(status,body.getBytes(StandardCharsets.UTF_8));}
    private HttpRequest sent()throws Exception {
        var capture=ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(capture.capture(),any(HttpResponse.BodyHandler.class));
        return capture.getValue();
    }
    @Test void requiresExactHttpsProjectAndNonemptyServerKey() {
        for(String url:List.of("http://cloud-test.supabase.co","https://evil.example","https://cloud-test.supabase.co@evil.example","https://cloud-test.supabase.co/path","https://cloud-test.supabase.co?key=secret"))
            assertThatThrownBy(()->new SupabaseAttachmentStorage(url,"caizhitong-attachments","test-key",client)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SupabaseAttachmentStorage("https://cloud-test.supabase.co","bucket","",client)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refusesPublicOrUnverifiableBucket()throws Exception {
        respond(200,"{\"id\":\"caizhitong-attachments\",\"public\":true}");
        assertThatThrownBy(storage::verifyPrivateBucket).isInstanceOf(IllegalStateException.class);
        respond(401,"{\"message\":\"provider internal detail\"}");
        assertThatThrownBy(storage::verifyPrivateBucket).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("provider internal detail");
    }
    @Test void validatesPrivateBucketBeforeUse()throws Exception {
        respond(200,"{\"id\":\"caizhitong-attachments\",\"public\":false}");
        storage.verifyPrivateBucket();
        assertThat(sent().uri().getPath()).isEqualTo("/storage/v1/bucket/caizhitong-attachments");
    }
    @Test void uploadUsesPrivateServerCredentialAndNeverUpserts()throws Exception {
        respond(200,"{}"); storage.save(object,new byte[]{1,2});
        var request=sent();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().getPath()).isEqualTo("/storage/v1/object/caizhitong-attachments/"+object);
        assertThat(request.headers().firstValue("apikey")).contains("unit-test-storage-key");
        assertThat(request.headers().firstValue("x-upsert")).contains("false");
        assertThat(request.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(request.uri().toString()).doesNotContain("unit-test-storage-key");
    }
    @Test void downloadsAuthenticatedBytesWithoutPublicOrSignedUrl()throws Exception {
        respond(200,new byte[]{3,4}); assertThat(storage.read(object)).containsExactly(3,4);
        assertThat(sent().uri().getPath()).isEqualTo("/storage/v1/object/authenticated/caizhitong-attachments/"+object);
    }
    @Test void headDistinguishesMissingObjectFromProviderOutage()throws Exception {
        respond(404,""); assertThat(storage.exists(object)).isFalse();
        respond(200,""); assertThat(storage.exists(object)).isTrue();
        respond(503,"unavailable"); assertThatThrownBy(()->storage.exists(object)).isInstanceOf(BusinessException.class).hasMessageContaining("暂时不可用");
    }
    @Test void keyValidationRunsBeforeAnyNetworkCall()throws Exception {
        for(String invalid:List.of("../"+object,"fake.png","/"+object,object+"?token=x")) {
            assertThatThrownBy(()->storage.read(invalid)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->storage.delete(invalid)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(client);
    }
    @Test void boundsDownloadedObjectSize()throws Exception {
        respond(200,new byte[5*1024*1024+1]);
        assertThatThrownBy(()->storage.read(object)).isInstanceOf(IOException.class).hasMessageContaining("size limit");
    }
    @Test void uploadFailureDoesNotLeakProviderBodyOrCredential()throws Exception {
        respond(403,"private-provider-error");
        assertThatThrownBy(()->storage.save(object,new byte[]{1})).isInstanceOf(IOException.class).hasMessageNotContaining("private-provider-error").hasMessageNotContaining("unit-test-storage-key");
    }
    @Test void cleanupUsesOfficialBucketDeleteEndpoint()throws Exception {
        respond(200,"[]"); storage.delete(object);
        assertThat(sent().method()).isEqualTo("DELETE");
    }
    @Test void defaultModeKeepsLocalStorageWithoutCloudCredentials() {
        new ApplicationContextRunner().withUserConfiguration(LocalAttachmentStorage.class,AttachmentStorageConfiguration.class,SupabaseAttachmentStorage.class)
            .run(context->{assertThat(context).hasSingleBean(AttachmentStorage.class).hasSingleBean(LocalAttachmentStorage.class).doesNotHaveBean(SupabaseAttachmentStorage.class).doesNotHaveBean("attachmentHttpClient");});
    }
    @Test void supabaseModeSelectsExactlyOnePrivateStorageBean()throws Exception {
        respond(200,"{\"id\":\"caizhitong-attachments\",\"public\":false}");
        new ApplicationContextRunner().withUserConfiguration(LocalAttachmentStorage.class,AttachmentStorageConfiguration.class,SupabaseAttachmentStorage.class)
            .withBean("attachmentHttpClient",HttpClient.class,()->client)
            .withPropertyValues("app.storage.mode=supabase","app.storage.supabase.url=https://cloud-test.supabase.co","app.storage.supabase.bucket=caizhitong-attachments","app.storage.supabase.key=unit-test-storage-key")
            .run(context->{assertThat(context).hasSingleBean(AttachmentStorage.class).hasSingleBean(SupabaseAttachmentStorage.class).doesNotHaveBean(LocalAttachmentStorage.class);});
    }
    @Test void legacyServerJwtUsesBearerInAdditionToGatewayApiKey()throws Exception {
        respond(200,new byte[]{1});
        var legacy=new SupabaseAttachmentStorage("https://cloud-test.supabase.co","caizhitong-attachments","eyJ-test-only-storage-credential",client);
        legacy.read(object);assertThat(sent().headers().firstValue("Authorization")).contains("Bearer eyJ-test-only-storage-credential");
    }
}
