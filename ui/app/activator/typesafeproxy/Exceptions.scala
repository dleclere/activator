package activator.typesafeproxy

sealed abstract class TypesafeComProxyException(message: String, cause: Throwable = null) extends Exception(message, cause)
class ProxyTimeout(msg: String, cause: Throwable = null) extends TypesafeComProxyException(msg, cause)
class ProxyInvalidCredentials(msg: String, cause: Throwable = null) extends TypesafeComProxyException(msg, cause)
class ProxyFailure(msg: String, cause: Throwable = null) extends TypesafeComProxyException(msg, cause)
class ProxyCanceled(msg: String, cause: Throwable = null) extends TypesafeComProxyException(msg, cause)

class CachePutFailure(msg: String, cause: Throwable = null) extends TypesafeComProxyException(msg, cause)
