package com.pesitwizard.server.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pesitwizard.server.entity.ApiKey;
import com.pesitwizard.server.repository.ApiKeyRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyService Tests")
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private SecurityProperties securityProperties;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        // Setup default mock for security properties
        SecurityProperties.ApiKeyConfig apiKeyConfig = new SecurityProperties.ApiKeyConfig();
        when(securityProperties.getApiKey()).thenReturn(apiKeyConfig);

        apiKeyService = new ApiKeyService(apiKeyRepository, securityProperties);
    }

    @Nested
    @DisplayName("Create API Key Tests")
    class CreateApiKeyTests {

        @Test
        @DisplayName("Should create new API key")
        void shouldCreateNewApiKey() {
            when(apiKeyRepository.existsByName("test-key")).thenReturn(false);
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
                ApiKey key = inv.getArgument(0);
                key.setId(1L);
                return key;
            });

            ApiKeyService.ApiKeyResult result = apiKeyService.createApiKey(
                    "test-key", "Test description", List.of("USER"),
                    null, null, null, null, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getApiKey()).isNotNull();
            assertThat(result.getPlainKey()).startsWith("psk_");
            assertThat(result.getApiKey().getName()).isEqualTo("test-key");

            verify(apiKeyRepository).save(any(ApiKey.class));
        }

        @Test
        @DisplayName("Should reject duplicate key name")
        void shouldRejectDuplicateKeyName() {
            when(apiKeyRepository.existsByName("existing-key")).thenReturn(true);

            assertThatThrownBy(() -> apiKeyService.createApiKey(
                    "existing-key", "desc", List.of("USER"),
                    null, null, null, null, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("Should set default role if none provided")
        void shouldSetDefaultRoleIfNoneProvided() {
            when(apiKeyRepository.existsByName("test-key")).thenReturn(false);
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

            apiKeyService.createApiKey("test-key", "desc", null, null, null, null, null, "admin");

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles()).contains("USER");
        }

        @Test
        @DisplayName("Should set expiration time when provided")
        void shouldSetExpirationTime() {
            when(apiKeyRepository.existsByName("test-key")).thenReturn(false);
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

            Instant expiresAt = Instant.now().plusSeconds(3600);
            apiKeyService.createApiKey("test-key", "desc", List.of("ADMIN"), expiresAt, null, null, null, "admin");

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(captor.capture());
            assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
        }
    }

    @Nested
    @DisplayName("Validate Key Tests")
    class ValidateKeyTests {

        @Test
        @DisplayName("Should return empty for null key")
        void shouldReturnEmptyForNullKey() {
            Optional<ApiKey> result = apiKeyService.validateKey(null, "192.168.1.1");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for blank key")
        void shouldReturnEmptyForBlankKey() {
            Optional<ApiKey> result = apiKeyService.validateKey("  ", "192.168.1.1");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for non-existent key")
        void shouldReturnEmptyForNonExistentKey() {
            when(apiKeyRepository.findActiveByKeyHash(anyString())).thenReturn(Optional.empty());

            Optional<ApiKey> result = apiKeyService.validateKey("psk_invalid", "192.168.1.1");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should validate active key")
        void shouldValidateActiveKey() {
            ApiKey apiKey = ApiKey.builder()
                    .id(1L)
                    .name("test-key")
                    .active(true)
                    .build();

            when(apiKeyRepository.findActiveByKeyHash(anyString())).thenReturn(Optional.of(apiKey));
            when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(apiKey);

            Optional<ApiKey> result = apiKeyService.validateKey("psk_somekey", null);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("test-key");
            verify(apiKeyRepository).save(apiKey); // last used updated
        }

        @Test
        @DisplayName("Should reject expired key")
        void shouldRejectExpiredKey() {
            ApiKey apiKey = ApiKey.builder()
                    .id(1L)
                    .name("expired-key")
                    .active(true)
                    .expiresAt(Instant.now().minusSeconds(3600))
                    .build();

            when(apiKeyRepository.findActiveByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            Optional<ApiKey> result = apiKeyService.validateKey("psk_expired", "192.168.1.1");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should reject IP not in allowed list")
        void shouldRejectIpNotInAllowedList() {
            ApiKey apiKey = ApiKey.builder()
                    .id(1L)
                    .name("ip-restricted")
                    .active(true)
                    .allowedIps("10.0.0.0/8")
                    .build();

            when(apiKeyRepository.findActiveByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            Optional<ApiKey> result = apiKeyService.validateKey("psk_restricted", "192.168.1.1");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get API Key Tests")
    class GetApiKeyTests {

        @Test
        @DisplayName("Should get API key by ID")
        void shouldGetApiKeyById() {
            ApiKey apiKey = ApiKey.builder().id(1L).name("test").build();
            when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));

            Optional<ApiKey> result = apiKeyService.getApiKey(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should get API key by name")
        void shouldGetApiKeyByName() {
            ApiKey apiKey = ApiKey.builder().id(1L).name("test-key").build();
            when(apiKeyRepository.findByName("test-key")).thenReturn(Optional.of(apiKey));

            Optional<ApiKey> result = apiKeyService.getApiKeyByName("test-key");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("test-key");
        }

        @Test
        @DisplayName("Should get all API keys")
        void shouldGetAllApiKeys() {
            List<ApiKey> keys = List.of(
                    ApiKey.builder().id(1L).name("key1").build(),
                    ApiKey.builder().id(2L).name("key2").build());
            when(apiKeyRepository.findAll()).thenReturn(keys);

            List<ApiKey> result = apiKeyService.getAllApiKeys();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should get active API keys")
        void shouldGetActiveApiKeys() {
            List<ApiKey> keys = List.of(ApiKey.builder().id(1L).name("active").active(true).build());
            when(apiKeyRepository.findByActiveTrue()).thenReturn(keys);

            List<ApiKey> result = apiKeyService.getActiveApiKeys();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("ApiKeyResult Record Tests")
    class ApiKeyResultTests {

        @Test
        @DisplayName("Should create ApiKeyResult")
        void shouldCreateApiKeyResult() {
            ApiKey apiKey = ApiKey.builder().id(1L).name("test").build();
            ApiKeyService.ApiKeyResult result = new ApiKeyService.ApiKeyResult(apiKey, "psk_plainkey");

            assertThat(result.getApiKey()).isEqualTo(apiKey);
            assertThat(result.getPlainKey()).isEqualTo("psk_plainkey");
        }
    }
}
