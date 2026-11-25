package es.uclm.esibuy.users.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.common.exception.UserNotFoundException;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.security.config.SecurityConfig;
import es.uclm.esibuy.security.service.EncryptionService;
import es.uclm.esibuy.security.service.LoginAttemptService;
import es.uclm.esibuy.security.service.RememberMeService;
import es.uclm.esibuy.security.service.SessionAuthenticationService;
import es.uclm.esibuy.security.service.TotpService;
import es.uclm.esibuy.security.service.TwoFactorAuthenticationService;
import es.uclm.esibuy.users.dto.UserProfileResponse;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import es.uclm.esibuy.security.service.EmailOtpService;

@WebMvcTest(UserController.class)
@Import({ ApiExceptionHandler.class, SecurityConfig.class, SessionAuthenticationService.class })
class UserProfileControllerTest {

        private static final String PROFILE_API = "/api/users/me/profile";

        private static final String TWO_FACTOR_SETUP_API = "/api/users/2fa/setup";

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private RememberMeService rememberMeService;

        @MockBean
        private UserService userService;

        @MockBean
        private LoginAttemptService loginAttemptService;

        @MockBean
        private TotpService totpService;

        @MockBean
        private EncryptionService encryptionService;

        @MockBean
        private TwoFactorAuthenticationService twoFactorAuthenticationService;

        @MockBean
        private EmailOtpService emailOtpService;

        private Authentication authenticationFor(UserRole role) {
                AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                                "user-1", "ana@example.com", role, true);
                return new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        }

        @Test
        void shouldReturnOwnProfileForClient() throws Exception {
                when(userService.getOwnProfile("user-1")).thenReturn(new UserProfileResponse(
                                "Ana", "García", "ana@example.com", "600123123", "*****678Z",
                                "avatar-client-default", CustomerType.PREMIUM, null, null, null, null, null, null));

                mockMvc.perform(get(PROFILE_API).with(authentication(authenticationFor(UserRole.CLIENT))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.firstName").value("Ana"))
                                .andExpect(jsonPath("$.maskedNationalId").value("*****678Z"))
                                .andExpect(jsonPath("$.customerType").value("PREMIUM"))
                                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                                .andExpect(jsonPath("$.totpSecret").doesNotExist())
                                .andExpect(jsonPath("$.nationalId").doesNotExist());
        }

        @Test
        void shouldReturnUnauthorizedWithoutSession() throws Exception {
                mockMvc.perform(get(PROFILE_API))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturnOwnProfileForSeller() throws Exception {
                when(userService.getOwnProfile("user-1")).thenReturn(new UserProfileResponse(
                                "Luis", "Martín", "luis@example.com", "600111222", null,
                                "avatar-seller-default", null, null, "Tienda Luis", "Electrónica", null, null, null));

                mockMvc.perform(get(PROFILE_API).with(authentication(authenticationFor(UserRole.SELLER))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.firstName").value("Luis"))
                                .andExpect(jsonPath("$.businessName").value("Tienda Luis"))
                                .andExpect(jsonPath("$.defaultCategory").value("Electrónica"));
        }

        @Test
        void shouldReturnOwnProfileForAdmin() throws Exception {
                when(userService.getOwnProfile("user-1")).thenReturn(new UserProfileResponse(
                                "Marta",
                                "López",
                                "marta@example.com",
                                "600333444",
                                null,
                                "avatar-admin-default",
                                null,
                                null,
                                null,
                                null,
                                "Madrid",
                                null, false));
                mockMvc.perform(get(PROFILE_API).with(authentication(authenticationFor(UserRole.ADMIN))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.firstName").value("Marta"))
                                .andExpect(jsonPath("$.headquarters").value("Madrid"));
        }

        @Test
        void shouldReturnNotFoundWhenSessionUserNoLongerExists() throws Exception {
                when(userService.getOwnProfile("user-1")).thenThrow(new UserNotFoundException("Usuario no encontrado"));

                mockMvc.perform(get(PROFILE_API).with(authentication(authenticationFor(UserRole.CLIENT))))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Usuario no encontrado"));
        }

        @Test
        void shouldUpdateOwnProfileSuccessfully() throws Exception {
                when(userService.updateOwnProfile(
                                org.mockito.ArgumentMatchers.eq("user-1"),
                                org.mockito.ArgumentMatchers.any()))
                                .thenReturn(new UserProfileResponse(
                                                "Ana María", "García López", "ana@example.com",
                                                "611222333", "*****678Z", "avatar-client-default",
                                                CustomerType.PREMIUM, null, null, null, null, null, null));

                mockMvc.perform(put(PROFILE_API)
                                .with(authentication(authenticationFor(UserRole.CLIENT)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "firstName": "Ana María",
                                                  "lastName": "García López",
                                                  "phone": "611222333"
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.firstName").value("Ana María"))
                                .andExpect(jsonPath("$.lastName").value("García López"))
                                .andExpect(jsonPath("$.phone").value("611222333"))
                                .andExpect(jsonPath("$.passwordHash").doesNotExist());

                org.mockito.Mockito.verify(userService).updateOwnProfile(
                                org.mockito.ArgumentMatchers.eq("user-1"),
                                org.mockito.ArgumentMatchers.any());
        }

        @Test
        void shouldRejectProfileUpdateWhenRequiredFieldsAreMissing() throws Exception {
                mockMvc.perform(put(PROFILE_API)
                                .with(authentication(authenticationFor(UserRole.CLIENT)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "firstName": "",
                                                  "lastName": ""
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.firstName").exists())
                                .andExpect(jsonPath("$.errors.lastName").exists());

                org.mockito.Mockito.verifyNoInteractions(userService);
        }

        @Test
        void shouldReturnUnauthorizedWhenUpdatingProfileWithoutSession() throws Exception {
                mockMvc.perform(put(PROFILE_API)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "firstName": "Ana",
                                                  "lastName": "García"
                                                }
                                                """))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturnNotFoundWhenUpdatingProfileOfMissingUser() throws Exception {
                when(userService.updateOwnProfile(
                                org.mockito.ArgumentMatchers.eq("user-1"),
                                org.mockito.ArgumentMatchers.any()))
                                .thenThrow(new UserNotFoundException("Usuario no encontrado"));

                mockMvc.perform(put(PROFILE_API)
                                .with(authentication(authenticationFor(UserRole.CLIENT)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "firstName": "Ana",
                                                  "lastName": "García"
                                                }
                                                """))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Usuario no encontrado"));
        }

        @Test
        void shouldDeleteOwnAccountAndInvalidateSession() throws Exception {
                MockHttpSession session = new MockHttpSession();

                doNothing().when(userService).deleteOwnAccount("user-1");

                mockMvc.perform(delete("/api/users/me")
                                .with(authentication(authenticationFor(UserRole.CLIENT)))
                                .session(session)
                                .with(csrf()))
                                .andExpect(status().isNoContent());

                org.mockito.Mockito.verify(userService).deleteOwnAccount("user-1");
                org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
        }

        @Test
        void shouldReturnUnauthorizedWhenDeletingAccountWithoutSession() throws Exception {
                mockMvc.perform(delete("/api/users/me")
                                .with(csrf()))
                                .andExpect(status().isUnauthorized());

                org.mockito.Mockito.verifyNoInteractions(userService);
        }

        @Test
        void shouldForbidAdminFromDeletingOwnAccount() throws Exception {
                MockHttpSession session = new MockHttpSession();

                mockMvc.perform(delete("/api/users/me")
                                .with(authentication(authenticationFor(UserRole.ADMIN)))
                                .session(session)
                                .with(csrf()))
                                .andExpect(status().isForbidden());

                org.mockito.Mockito.verifyNoInteractions(userService);
        }

        // HU08: el endpoint pasa al servicio el código que llega en el cuerpo
        @Test
        void shouldSetupTwoFactorWithCodeFromBody() throws Exception {
                when(twoFactorAuthenticationService.setup("user-1", "123456"))
                                .thenReturn(new TwoFactorAuthenticationService.SetupResult(
                                                TwoFactorAuthenticationService.Status.SUCCESS, "NEWSECRET"));

                mockMvc.perform(post(TWO_FACTOR_SETUP_API)
                                .with(authentication(authenticationFor(UserRole.CLIENT)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "code": "123456" }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.secret").value("NEWSECRET"))
                                .andExpect(jsonPath("$.qrUri").value(
                                                "otpauth://totp/ESIBuy:ana@example.com?secret=NEWSECRET&issuer=ESIBuy"));
        }

        // La primera configuración no tiene código que pedir: el cuerpo puede no llegar
        @Test
        void shouldSetupTwoFactorWithoutBody() throws Exception {
                when(twoFactorAuthenticationService.setup("user-1", null))
                                .thenReturn(new TwoFactorAuthenticationService.SetupResult(
                                                TwoFactorAuthenticationService.Status.SUCCESS, "FIRSTSECRET"));

                mockMvc.perform(post(TWO_FACTOR_SETUP_API)
                                .with(authentication(authenticationFor(UserRole.CLIENT)))
                                .with(csrf()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.secret").value("FIRSTSECRET"));
        }

        // HU08: si el código no es válido no cambia nada y se dice por qué
        @Test
        void shouldRejectTwoFactorReconfigurationWithInvalidCode() throws Exception {
                when(twoFactorAuthenticationService.setup("user-1", "000000"))
                                .thenReturn(new TwoFactorAuthenticationService.SetupResult(
                                                TwoFactorAuthenticationService.Status.INVALID_CODE, null));

                mockMvc.perform(post(TWO_FACTOR_SETUP_API)
                                .with(authentication(authenticationFor(UserRole.CLIENT)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "code": "000000" }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("El código introducido es incorrecto"))
                                .andExpect(jsonPath("$.secret").doesNotExist());
        }
}