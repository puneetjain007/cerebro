package services.exception

case class InsufficientPermissionsException(
  username: String,
  operation: String,
  requiredRole: String
) extends RuntimeException(
  s"Operation Not Permitted"
)
