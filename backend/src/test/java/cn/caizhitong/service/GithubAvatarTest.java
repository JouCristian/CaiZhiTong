package cn.caizhitong.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GithubAvatarTest {
    @Test void onlyTrustedHttpsAvatarHostIsAccepted(){
        assertThat(GithubIdentityService.safeAvatar("https://avatars.githubusercontent.com/u/7000001?v=4")).isNotNull();
        for(String value:new String[]{"http://avatars.githubusercontent.com/u/1","https://evil.example/u/1","https://avatars.githubusercontent.com@evil.example/u/1","javascript:alert(1)","https://avatars.githubusercontent.com:444/u/1","https://avatars.githubusercontent.com/u/1#secret"})assertThat(GithubIdentityService.safeAvatar(value)).isNull();
        assertThat(GithubIdentityService.safeAvatar(null)).isNull();
    }
}
