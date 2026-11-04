package cn.caizhitong;
import cn.caizhitong.entity.SysUser;
import cn.caizhitong.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.cors.allowed-origins=http://localhost:5173","app.demo.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class MaterialIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { AuthIntegrationTest.properties(registry); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired PasswordEncoder passwords;
    private final Map<String,String> tokens=new HashMap<>();
    @BeforeAll void seedAndLogin() throws Exception {
        for (String role:List.of("ADMIN","LAB_TECH","STUDENT","TEACHER","PURCHASER","GUEST")) {
            String name="test_"+role.toLowerCase(Locale.ROOT);
            SysUser user=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,name));
            if (user==null) {
                user=new SysUser(); user.setUsername(name); user.setDisplayName("业务测试账户");
                user.setPasswordHash(passwords.encode("test-only-password-2026")); user.setEnabled(true); users.insert(user);
            }
            users.assignRole(user.getId(),role);
            String body=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",name,"password","test-only-password-2026"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            tokens.put(role,json.readTree(body).at("/data/accessToken").asText());
        }
    }
    ResultActions call(String method,String path,String role,Object body) throws Exception {
        var builder=switch(method) { case "POST" -> post(path); case "PUT" -> put(path); case "PATCH" -> patch(path); case "DELETE" -> delete(path); default -> get(path); };
        builder.header("Authorization","Bearer "+tokens.get(role));
        if (body!=null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(builder);
    }
    JsonNode data(ResultActions result) throws Exception { return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data"); }
    long category(String name,Long parent) throws Exception {
        var input=new LinkedHashMap<String,Object>(); input.put("name",name); input.put("parentId",parent); input.put("description","测试类目"); input.put("sortOrder",0);
        return data(call("POST","/api/material-categories","ADMIN",input)).get("id").asLong();
    }
    Map<String,Object> input(long category,String name) {
        var input=new LinkedHashMap<String,Object>();
        input.put("categoryId",category); input.put("name",name); input.put("brand","测试品牌"); input.put("model","R-10K");
        input.put("spec","10kΩ ±1%"); input.put("unit","个"); input.put("unitPrice","12.3400"); input.put("safeStock","5");
        input.put("status","ACTIVE"); input.put("description","可重复查询的测试档案");
        return input;
    }
    JsonNode create(long category,String name) throws Exception { return data(call("POST","/api/materials","ADMIN",input(category,name))); }

    @Test void createsMaterialWithGeneratedCodeAndPersistsExactMoney() throws Exception {
        long c=category("基础元件",null);
        JsonNode row=create(c,"精密电阻");
        assertThat(row.get("materialCode").asText()).matches("MAT-[0-9]{10}");
        assertThat(row.get("unitPrice").isTextual()).isTrue();
        assertThat(row.get("unitPrice").asText()).isEqualTo("12.3400");
        assertThat(row.get("currency").asText()).isEqualTo("CNY");
        assertThat(row.has("stock")).isFalse();
        JsonNode persisted=data(call("GET","/api/materials/"+row.get("id").asLong(),"ADMIN",null));
        assertThat(persisted.get("materialCode")).isEqualTo(row.get("materialCode"));
        assertThat(persisted.get("name").asText()).isEqualTo("精密电阻");
        assertThat(jdbc.queryForObject("SELECT unit_price::text FROM material_item WHERE id=?",String.class,row.get("id").asLong())).isEqualTo("12.3400");
    }
    @Test void editsAllFieldsWithoutChangingCodeAndClearsNullableFields() throws Exception {
        long c=category("可编辑类目",null);
        JsonNode row=create(c,"旧名称");
        var body=input(c,"新名称"); body.put("unitPrice","0.0001"); body.put("brand",null); body.put("model",""); body.put("description","");
        JsonNode updated=data(call("PUT","/api/materials/"+row.get("id").asLong(),"LAB_TECH",body));
        assertThat(updated.get("name").asText()).isEqualTo("新名称");
        assertThat(updated.get("materialCode")).isEqualTo(row.get("materialCode"));
        assertThat(updated.get("unitPrice").asText()).isEqualTo("0.0001");
        assertThat(updated.has("brand")).isFalse(); assertThat(updated.has("model")).isFalse(); assertThat(updated.has("description")).isFalse();
        assertThat(data(call("GET","/api/materials/"+row.get("id").asLong(),"STUDENT",null)).get("name").asText()).isEqualTo("新名称");
    }
    @Test void serverPaginationSearchFiltersAndStableSorting() throws Exception {
        long root=category("分页类目",null), child=category("分页子类",root);
        List<Long> ids=new ArrayList<>();
        for (int i=0;i<23;i++) {
            var body=input(child,"分页材料-"+String.format("%02d",i));
            body.put("brand",i%2==0?"A品牌":"B品牌");
            body.put("status",i%2==0?"ACTIVE":"DISABLED");
            ids.add(data(call("POST","/api/materials","ADMIN",body)).get("id").asLong());
        }
        JsonNode page=data(call("GET","/api/materials?categoryId="+root+"&size=10&page=2&order=asc","ADMIN",null));
        assertThat(page.get("total").asLong()).isEqualTo(23);
        assertThat(page.get("records").size()).isEqualTo(10);
        assertThat(page.at("/records/0/id").asLong()).isEqualTo(ids.get(10));
        JsonNode byName=data(call("GET","/api/materials?keyword=分页材料-07","STUDENT",null));
        assertThat(byName.get("total").asLong()).isEqualTo(1);
        String code=byName.at("/records/0/materialCode").asText();
        assertThat(data(call("GET","/api/materials?keyword="+code,"ADMIN",null)).get("total").asLong()).isEqualTo(1);
        assertThat(data(call("GET","/api/materials?categoryId="+child+"&brand=A品牌&status=ACTIVE","ADMIN",null)).get("total").asLong()).isEqualTo(12);
    }
    @Test void rejectsInvalidValuesAndPagination() throws Exception {
        long c=category("校验类目",null);
        for (var entry:Map.<String,Object>of("name"," ","unitPrice","-0.01","safeStock","-1","currency","cny","status","ARCHIVED").entrySet()) {
            var body=input(c,"非法材料"); body.put(entry.getKey(),entry.getValue());
            call("POST","/api/materials","ADMIN",body).andExpect(status().isBadRequest());
        }
        var body=input(c,"精度越界"); body.put("unitPrice","0.00001");
        call("POST","/api/materials","ADMIN",body).andExpect(status().isBadRequest());
        call("GET","/api/materials?page=0","ADMIN",null).andExpect(status().isBadRequest());
        call("GET","/api/materials?size=101","ADMIN",null).andExpect(status().isBadRequest());
        call("GET","/api/materials?sort=name","ADMIN",null).andExpect(status().isBadRequest());
        call("GET","/api/materials?order=DROP","ADMIN",null).andExpect(status().isBadRequest());
    }
    @Test void duplicateAndClientSuppliedCodesAreRejected() throws Exception {
        long c=category("编码校验",null); JsonNode row=create(c,"编码材料");
        var body=input(c,"重复编码"); body.put("materialCode",row.get("materialCode").asText());
        call("POST","/api/materials","ADMIN",body).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("材料编码已存在"));
        body.put("materialCode","CUSTOM-CODE");
        call("POST","/api/materials","ADMIN",body).andExpect(status().isBadRequest());
    }
    @Test void databaseAlsoEnforcesUniqueMaterialCode() throws Exception {
        long c=category("编码唯一约束",null); JsonNode row=create(c,"唯一约束材料");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO material_item(category_id,material_code,name,unit,unit_price) VALUES (?,?,?,?,?)",
            c,row.get("materialCode").asText(),"重复档案","个",new java.math.BigDecimal("1")))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void protectsCategoryDepthAndCyclesAndDuplicateSiblingNames() throws Exception {
        long root=category("大类",null), child=category("子类",root);
        call("POST","/api/material-categories","ADMIN",Map.of("name","第三层基础分类","parentId",child)).andExpect(status().isBadRequest());
        call("PUT","/api/material-categories/"+root,"ADMIN",Map.of("name","大类","parentId",child)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("类目不能引用自身或其子节点，禁止循环引用"));
        call("PUT","/api/material-categories/"+child,"ADMIN",Map.of("name","子类","parentId",child)).andExpect(status().isBadRequest());
        call("POST","/api/material-categories","ADMIN",Map.of("name","子类","parentId",root)).andExpect(status().isConflict());
        long other=category("另一大类",null);
        call("PUT","/api/material-categories/"+root,"ADMIN",Map.of("name","大类","parentId",other)).andExpect(status().isBadRequest());
        JsonNode tree=data(call("GET","/api/material-categories/tree","ADMIN",null));
        assertThat(tree.toString()).contains("子类");
    }
    @Test void allowsRenameMoveToRootAndDeleteEmptyCategory() throws Exception {
        long root=category("父类",null), child=category("待移动子类",root);
        var body=new LinkedHashMap<String,Object>(); body.put("name","已移动大类"); body.put("parentId",null); body.put("sortOrder",10);
        JsonNode updated=data(call("PUT","/api/material-categories/"+child,"ADMIN",body));
        assertThat(updated.has("parentId")).isFalse();
        call("DELETE","/api/material-categories/"+child,"ADMIN",null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material_category WHERE id=?",Long.class,child)).isZero();
    }
    @Test void cannotDeleteCategoryWithChildrenOrMaterialsEvenAfterArchive() throws Exception {
        long root=category("不可删除大类",null), child=category("受保护子类",root);
        call("DELETE","/api/material-categories/"+root,"ADMIN",null).andExpect(status().isConflict());
        JsonNode row=create(child,"历史关联材料");
        call("DELETE","/api/material-categories/"+child,"ADMIN",null).andExpect(status().isConflict());
        data(call("PATCH","/api/materials/"+row.get("id").asLong()+"/status","ADMIN",Map.of("status","ARCHIVED")));
        call("DELETE","/api/material-categories/"+child,"ADMIN",null).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"STUDENT","TEACHER","PURCHASER","GUEST"})
    void readonlyRolesCanReadButCannotWrite(String role) throws Exception {
        long c=category("只读角色分类",null); JsonNode row=create(c,"只读角色材料"); long id=row.get("id").asLong();
        call("GET","/api/materials/"+id,role,null).andExpect(status().is(role.equals("GUEST")?403:200));
        call("GET","/api/material-categories/tree",role,null).andExpect(status().is(role.equals("GUEST")?403:200));
        call("POST","/api/materials",role,input(c,"越权材料")).andExpect(status().isForbidden());
        call("PUT","/api/materials/"+id,role,input(c,"越权编辑")).andExpect(status().isForbidden());
        call("PATCH","/api/materials/"+id+"/status",role,Map.of("status","DISABLED")).andExpect(status().isForbidden());
        call("POST","/api/material-categories",role,Map.of("name","越权分类")).andExpect(status().isForbidden());
        call("DELETE","/api/material-categories/"+c,role,null).andExpect(status().isForbidden());
    }
    @Test void labCanCreateEditAndDisableButCannotArchiveOrMaintainCategories() throws Exception {
        long c=category("实验员分类",null);
        long id=data(call("POST","/api/materials","LAB_TECH",input(c,"实验员材料"))).get("id").asLong();
        call("PUT","/api/materials/"+id,"LAB_TECH",input(c,"实验员修改")).andExpect(status().isOk());
        call("PATCH","/api/materials/"+id+"/status","LAB_TECH",Map.of("status","DISABLED")).andExpect(status().isOk());
        call("PATCH","/api/materials/"+id+"/status","LAB_TECH",Map.of("status","ARCHIVED")).andExpect(status().isForbidden());
        call("POST","/api/material-categories","LAB_TECH",Map.of("name","越权分类")).andExpect(status().isForbidden());
        call("PUT","/api/material-categories/"+c,"LAB_TECH",Map.of("name","越权修改")).andExpect(status().isForbidden());
    }
    @Test void archivedMaterialsRetainHistoryAndCannotBeEditedOrReactivated() throws Exception {
        long c=category("归档测试分类",null); JsonNode row=create(c,"归档测试材料"); long id=row.get("id").asLong();
        data(call("PATCH","/api/materials/"+id+"/status","ADMIN",Map.of("status","ARCHIVED")));
        assertThat(data(call("GET","/api/materials/"+id,"ADMIN",null)).get("materialCode")).isEqualTo(row.get("materialCode"));
        call("PUT","/api/materials/"+id,"ADMIN",input(c,"改动历史")).andExpect(status().isConflict());
        call("PATCH","/api/materials/"+id+"/status","ADMIN",Map.of("status","ACTIVE")).andExpect(status().isConflict());
        assertThat(data(call("GET","/api/materials?status=ARCHIVED","ADMIN",null)).get("total").asLong()).isEqualTo(1);
    }
    @Test void facetsAreDerivedFromStoredMaterialProperties() throws Exception {
        long c=category("属性类目",null); create(c,"属性材料一"); create(c,"属性材料二");
        JsonNode facets=data(call("GET","/api/materials/facets","ADMIN",null));
        assertThat(facets.size()).isEqualTo(1);
        assertThat(facets.get(0).get("brand").asText()).isEqualTo("测试品牌");
        assertThat(facets.get(0).get("materialCount").asLong()).isEqualTo(2);
    }
    @Test void missingCategoryAndMaterialReturn404() throws Exception {
        call("POST","/api/materials","ADMIN",input(99999999L,"无效分类材料")).andExpect(status().isNotFound());
        call("GET","/api/materials/99999999","ADMIN",null).andExpect(status().isNotFound());
    }
    @Test void newWriteMethodsPassConfiguredCorsPreflight() throws Exception {
        for (String method:List.of("PUT","PATCH","DELETE")) {
            mvc.perform(options("/api/materials/1").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method",method).header("Access-Control-Request-Headers","Authorization,Content-Type"))
                .andExpect(status().isOk());
        }
    }
}

