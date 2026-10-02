export function validatePasswordChange(currentPassword, newPassword, confirmation) {
  const errors = {};

  if (Array.from(newPassword).length < 10) {
    errors.newPassword = "minimum-length";
  } else if (new TextEncoder().encode(newPassword).length > 72) {
    errors.newPassword = "maximum-bytes";
  } else if (newPassword === currentPassword) {
    errors.newPassword = "must-differ";
  }

  if (confirmation !== newPassword) {
    errors.confirmation = "mismatch";
  }

  return errors;
}
