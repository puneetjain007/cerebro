package services.exception

case class InsufficientPermissionsException(
  username: String,
  operation: String,
  requiredRole: String
) extends RuntimeException(
  s"User '$username' does not have permission to perform operation '$operation'. Required role: $requiredRole"
)
