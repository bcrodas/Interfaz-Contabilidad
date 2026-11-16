package ec.contabilidad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record UserRequest(
    @NotBlank @Size(max = 100) String username,
    String password,
    @NotBlank String role,
    boolean active
) {}

record UserDto(long id, String username, String role, boolean active) {}
