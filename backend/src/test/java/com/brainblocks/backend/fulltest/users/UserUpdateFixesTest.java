package com.brainblocks.backend.fulltest.users;

import com.brainblocks.backend.dto.request.user.UpdateProfileRequest;
import com.brainblocks.backend.dto.request.user.UpdateUserStatusRequest;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.ai.GeminiService;
import com.brainblocks.backend.service.location.LocationDirectory;
import com.brainblocks.backend.service.mail.MailService;
import com.brainblocks.backend.service.user.AdminUserService;
import com.brainblocks.backend.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-update-fixes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@ActiveProfiles("test")
class UserUpdateFixesTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final JsonMapper JSON = new JsonMapper();
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AdminUserService adminUsers;
    @Autowired LocationDirectory locations;
    @MockitoSpyBean UserRepository users;
    @MockitoBean MailService mailService;
    @MockitoBean GeminiService geminiService;
    private MockMvc mvc;

    record Response(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }
    record Account(String token, long id) {}

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void childBornTodayCanBeCreated() throws Exception {
        Account parent = customer();
        String today = LocalDate.now().toString();
        Response created = call("POST", "/api/children", parent.token(),
                Map.of("name", "Newborn", "birthDate", today, "gender", "MALE"));
        expect(created, 201);
        Response saved = call("GET", "/api/children/" + created.data().path("id").asLong(), parent.token(), null);
        expect(saved, 200);
        assertThat(saved.data().path("birthDate").asString()).isEqualTo(today);
    }

    @Test
    void childBornTomorrowIsRejected() throws Exception {
        Account parent = customer();
        expect(call("POST", "/api/children", parent.token(), Map.of("name", "Future child",
                "birthDate", LocalDate.now().plusDays(1).toString(), "gender", "FEMALE")), 400);
        Response children = call("GET", "/api/children", parent.token(), null);
        expect(children, 200);
        assertThat(children.data().size()).isZero();
    }

    @Test
    void profileUpdateAndAdminLockUnlockStillWork() throws Exception {
        Account parent = customer();
        expect(call("PUT", "/api/me", parent.token(),
                Map.of("fullName", "Updated Parent", "phone", "0901234567")), 200);
        Response saved = call("GET", "/api/me", parent.token(), null);
        expect(saved, 200);
        assertThat(saved.data().path("fullName").asString()).isEqualTo("Updated Parent");
        assertThat(saved.data().path("phone").asString()).isEqualTo("0901234567");

        Response login = call("POST", "/api/auth/login", null,
                Map.of("email", "admin@test.local", "password", "test-admin-password"));
        expect(login, 200);
        String admin = login.data().path("token").asString();
        String statusPath = "/api/admin/users/" + parent.id() + "/status";
        Response locked = call("PATCH", statusPath, admin, Map.of("enabled", false));
        expect(locked, 200);
        assertThat(locked.data().path("enabled").asBoolean()).isFalse();
        expect(call("GET", "/api/me", parent.token(), null), 401);
        Response unlocked = call("PATCH", statusPath, admin, Map.of("enabled", true));
        expect(unlocked, 200);
        assertThat(unlocked.data().path("enabled").asBoolean()).isTrue();
        Response restored = call("GET", "/api/me", parent.token(), null);
        expect(restored, 200);
        assertThat(restored.data().path("fullName").asString()).isEqualTo("Updated Parent");
        assertThat(restored.data().path("phone").asString()).isEqualTo("0901234567");
    }

    @Test
    void concurrentProfileUpdateCannotUndoAdminLock() throws Exception {
        Account parent = customer();
        CountDownLatch profileRead = new CountDownLatch(1);
        CountDownLatch releaseProfile = new CountDownLatch(1);
        CountDownLatch adminLockOrFinished = new CountDownLatch(1);
        AtomicReference<Thread> adminThread = new AtomicReference<>();
        AtomicBoolean firstResolve = new AtomicBoolean(true);

        var province = locations.provinces().stream().filter(p -> !p.districts().isEmpty()).findFirst().orElseThrow();
        var district = province.districts().getFirst();
        Integer wardCode = district.hasWards() ? district.wards().getFirst().code() : null;
        var address = locations.resolve(province.code(), district.code(), wardCode);
        LocationDirectory hookLocations = mock(LocationDirectory.class);
        doAnswer(invocation -> {
            // resolve được gọi sau findById, trước khi Hibernate flush User.
            if (firstResolve.getAndSet(false)) {
                profileRead.countDown();
                await(releaseProfile);
            }
            return address;
        }).when(hookLocations).resolve(any(), any(), any());

        var repositoryAnswer = mockingDetails(users).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            if (Thread.currentThread() == adminThread.get()) {
                // Chỉ báo đã tới khóa; vẫn chạy SELECT FOR UPDATE thật.
                adminLockOrFinished.countDown();
            }
            return repositoryAnswer.answer(invocation);
        }).when(users).lockId(parent.id());

        CurrentUserProvider current = mock(CurrentUserProvider.class);
        when(current.getCurrentUserId()).thenReturn(parent.id());
        UserService profileService = new UserService(users, mock(PasswordEncoder.class), current, hookLocations);
        UpdateProfileRequest update = new UpdateProfileRequest("Concurrent Parent", "0901234567",
                province.code(), district.code(), wardCode, "Test address");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var profile = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(
                    status -> profileService.updateMyProfile(update)));
            await(profileRead);
            var admin = pool.submit(() -> {
                adminThread.set(Thread.currentThread());
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(
                            status -> adminUsers.updateStatus(parent.id(), new UpdateUserStatusRequest(false)));
                } finally {
                    // Code cũ không khóa: chỉ nhả A sau khi B đã commit.
                    adminLockOrFinished.countDown();
                }
            });
            await(adminLockOrFinished);
            // Code mới: A giữ khóa nên phải nhả A để B tiếp tục và commit sau A.
            releaseProfile.countDown();
            profile.get(30, TimeUnit.SECONDS);
            admin.get(30, TimeUnit.SECONDS);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                User saved = users.findById(parent.id()).orElseThrow();
                assertThat(saved.isEnabled()).isFalse();
                assertThat(saved.getFullName()).isEqualTo("Concurrent Parent");
                assertThat(saved.getPhone()).isEqualTo("0901234567");
            });
        } finally {
            releaseProfile.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void profileUpdateLocksBeforeReadingUser() {
        UserRepository repository = mock(UserRepository.class);
        CurrentUserProvider current = mock(CurrentUserProvider.class);
        Customer customer = Customer.builder().id(42L).fullName("Parent").build();
        when(current.getCurrentUserId()).thenReturn(42L);
        when(repository.findById(42L)).thenReturn(Optional.of(customer));
        UserService service = new UserService(repository, mock(PasswordEncoder.class), current,
                mock(LocationDirectory.class));

        service.updateMyProfile(new UpdateProfileRequest("Updated", "0901234567", null, null, null, null));

        var order = inOrder(repository);
        order.verify(repository).lockId(42L);
        order.verify(repository).findById(42L);
        order.verifyNoMoreInteractions();
        assertThat(customer.getFullName()).isEqualTo("Updated");
    }

    @Test
    void adminStatusUpdateLocksBeforeReadingUser() {
        UserRepository repository = mock(UserRepository.class);
        Customer customer = Customer.builder().id(42L).fullName("Parent").build();
        when(repository.findById(42L)).thenReturn(Optional.of(customer));
        AdminUserService service = new AdminUserService(repository, mock(CustomerRepository.class), mock(UserService.class));

        service.updateStatus(42L, new UpdateUserStatusRequest(false));

        var order = inOrder(repository);
        order.verify(repository).lockId(42L);
        order.verify(repository).findById(42L);
        order.verifyNoMoreInteractions();
        assertThat(customer.isEnabled()).isFalse();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("Chờ điểm đồng bộ").isTrue();
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).contentType("application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(JSON.writeValueAsString(body));
        var result = mvc.perform(request).andReturn().getResponse();
        String content = result.getContentAsString(StandardCharsets.UTF_8);
        return new Response(result.getStatus(), content.isBlank() ? JSON.createObjectNode() : JSON.readTree(content));
    }

    private void expect(Response response, int status) {
        assertThat(response.status()).as("response: %s", response.body()).isEqualTo(status);
    }

    private Account customer() throws Exception {
        String email = "user-fixes-" + UUID.randomUUID() + "@test.local";
        expect(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Parent", "email", email, "password", PASSWORD)), 201);
        Response login = call("POST", "/api/auth/login", null, Map.of("email", email, "password", PASSWORD));
        expect(login, 200);
        return new Account(login.data().path("token").asString(), login.data().path("userId").asLong());
    }
}
