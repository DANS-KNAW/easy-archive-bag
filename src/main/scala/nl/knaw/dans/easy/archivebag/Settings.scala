package nl.knaw.dans.easy.archivebag

import java.io.File
import java.net.URL

case class Settings(username: String,
                    password: String,
                    checkInterval: Int,
                    maxCheckCount: Int,
                    bagDir: File,
                    slug: Option[String],
                    storageDepositService: URL)
