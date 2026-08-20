package com.pei.zfile;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.pei.zfile.file.mapper.FileNodeMapper;
import com.pei.zfile.audit.mapper.AuditMapper;
import com.pei.zfile.share.mapper.ShareItemMapper;
import com.pei.zfile.share.mapper.ShareMapper;
import com.pei.zfile.user.mapper.UserMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class ZFileApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private FileNodeMapper fileNodeMapper;

    @MockitoBean
    private ShareMapper shareMapper;

    @MockitoBean
    private ShareItemMapper shareItemMapper;

    @MockitoBean
    private AuditMapper auditMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void openApiDocsAreAvailableWithoutApiPrefix() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
