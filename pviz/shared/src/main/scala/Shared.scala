package pviz

trait Names {
  val CONFIG = "root-config-json"
  def CONFIG_STMT = s"config-stmt-json"
  def CONFIG_SIG(auth: Int) = s"$auth-config-sig-ucb"

  def PAUSE = "root-pause"

  def ERROR = "root-error"
  def ERROR(auth: Int) = s"$auth-error"

  def SHARE(item: Int, auth: Int) = s"$auth-$item-share-json"
  def SHARE_STMT(item: Int, auth: Int) = s"$auth-$item-share-stmt-json"
  def SHARE_SIG(item: Int, auth: Int) = s"$auth-$item-share-sig-ucb"

  def PUBLIC_KEY(item: Int) = s"1-$item-public_key-ucb"
  def PUBLIC_KEY_STMT(item: Int) = s"1-$item-public_key-stmt-json"
  def PUBLIC_KEY_SIG(item: Int, auth: Int) = s"$auth-$item-public_key-sig-ucb"

  def BALLOTS(item: Int) = s"bb-$item-ballots-ucb"
  def BALLOTS_STMT(item: Int) = s"bb-$item-ballots-stmt-json"
  def BALLOTS_SIG(item: Int) = s"bb-$item-ballots-sig"


  def PERM_DATA(item: Int, auth: Int) = s"$auth-$item-perm_data"

  def MIX(item: Int, auth: Int) = s"$auth-$item-mix-json"
  def MIX_STMT(item: Int, auth: Int) = s"$auth-$item-mix-stmt-json"

  def MIX_SIG(item: Int, auth: Int, signingAuth: Int) = s"$signingAuth-$item-mix-$auth-sig-ucb"

  def DECRYPTION(item: Int, auth: Int) = s"$auth-$item-decryption-json"
  def DECRYPTION_STMT(item: Int, auth: Int) = s"$auth-$item-decryption-stmt-json"
  def DECRYPTION_SIG(item: Int, auth: Int) = s"$auth-$item-decryption-sig-ucb"

  def PLAINTEXTS(item: Int) = s"1-$item-plaintexts-json"
  def PLAINTEXTS_STMT(item: Int) = s"1-$item-plaintext-stmt-json"
  def PLAINTEXTS_SIG(item: Int, auth: Int) = s"$auth-$item-plaintext-sig-ucb"

  def IDLE = "idle"
  def DONE = "done"
}

case class Config(id: String, name: String, modulus: String, generator: String,
  items: Int, ballotbox: String, trustees: Array[String]) {
  override def toString() = s"Config($id $name $items)"
}

case class Status(present: Seq[String], active: Seq[String])

trait Api{
  def getStatus(root: String): Status
  def getConfig(root: String): Config
}