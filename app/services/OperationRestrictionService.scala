package services

import javax.inject.{Inject, Singleton}
import play.api.Configuration

/**
 * Service to check if operations are restricted based on configuration.
 * 
 * Configuration options:
 *   - restrictions.read-only: enables read-only mode (only GET operations allowed)
 *   - Can also be set via environment variable CEREBRO_READ_ONLY=true
 */
@Singleton
class OperationRestrictionService @Inject()(config: Configuration) {

  private val readOnlyMode: Boolean =
    config.getOptional[Boolean]("restrictions.read-only").getOrElse(false)

  /**
   * Check if the system is in read-only mode.
   * @return true if read-only mode is enabled
   */
  def isReadOnly: Boolean = readOnlyMode

  /**
   * Check if write operations are allowed.
   * @return true if write operations are permitted, false if in read-only mode
   */
  def isWriteAllowed: Boolean = !readOnlyMode

  /**
   * Check if a specific HTTP method is allowed.
   * @param method the HTTP method (GET, POST, PUT, DELETE)
   * @return true if the method is permitted
   */
  def isMethodAllowed(method: String): Boolean = {
    method.toUpperCase match {
      case "GET" => true
      case _ => !readOnlyMode  // POST, PUT, DELETE blocked in read-only mode
    }
  }
}

/**
 * Exception thrown when an operation is blocked by restrictions.
 */
class OperationRestrictedException(operation: String)
  extends RuntimeException(s"Operation '$operation' is restricted. System is in read-only mode.")

