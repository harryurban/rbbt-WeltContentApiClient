package de.welt.contentapi.raw.models

import java.time.Instant

/**
  * Tree structure of the escenic channel/section tree. Simple representation:
  * |-- '/' root (WON_frontpage)
  * |--|-- '/sport/'
  * |--|--|-- '/sport/fussball/'
  * |--|--|-- '/sport/formel1/'
  * |--|--|-- '/sport/golf/'
  * |--|-- '/wirtschaft/'
  * |--|-- '/politik/'
  *
  * @param id       mandatory id with the channel path. E.g. /sport/fussball/
  * @param config   channel configuration for the clients. Used by Funkotron.
  * @param stages   stage configuration for the channel. Used by Digger.
  * @param metadata meta data for CMCF and Janus. Needed for some merge/update logic.
  * @param parent   the maybe parent of the current channel. Root channel has no parent. (no shit sherlock!)
  * @param children all children of the current channel
  */
case class RawChannel(id: RawChannelId,
                      config: Option[RawChannelConfiguration] = None,
                      stages: Option[Seq[RawChannelStage]] = None,
                      metadata: Option[Metadata] = None,
                      parent: Option[RawChannel] = None,
                      children: Option[Seq[RawChannel]] = None) {
  lazy val unwrappedStages: Seq[RawChannelStage] = stages.getOrElse(Nil)
  lazy val unwrappedChildren: Seq[RawChannel] = children.getOrElse(Nil)

  /**
    * @param search channel path. E.g. '/sport/fussball/'
    * @return maybe channel for the search string
    */
  def findByPath(search: String): Option[RawChannel] = findByPath(
    search.split('/').filter(_.nonEmpty).toList match {
      case Nil ⇒ Nil
      case head :: tail ⇒ tail.scanLeft(s"/$head/")((path, s) ⇒ path + s + "/")
    }
  )

  private def findByPath(sectionPath: Seq[String]): Option[RawChannel] = {
    sectionPath match {
      case Nil ⇒
        Some(this)
      case head :: Nil ⇒
        unwrappedChildren.find(_.id.path == head)
      case head :: tail ⇒
        unwrappedChildren.find(_.id.path == head).flatMap(_.findByPath(tail))
    }
  }

  // TODO: (mana) (re)-write or copy/paste the old update/merge logic here.

}

/**
  * @param path      unique path of the channel. Always with a trailing slash. E.g. '/sport/fussball/'
  * @param label     label of the channel. This is the display name from escenic. (provided by SDP)
  * @param escenicId escenic id of the section. E.g. root channel ('/') with id 5. Default value '-1' is a error state.
  */
case class RawChannelId(path: String,
                        label: String,
                        escenicId: Long = -1)

/**
  * @param metaTags   `<meta>` tag overrides of the channel.
  * @param header     content header (not the real page header) configuration.
  * @param commercial commercial configuration for the channel. Used some override logic.
  */
case class RawChannelConfiguration(metaTags: Option[RawChannelMetaTags] = None,
                                   header: Option[RawChannelHeader] = None,
                                   commercial: Option[RawChannelCommercial] = None)

/**
  * The (ASMI) ad tag is a string with the root section and type of the page (section or content page).
  * When a channel defines an ad tag we override the root section with its own section.
  * We need this for some channel targeting. E.g. '/sport/formel1/' needs his own ad tag.
  *
  * @param definesAdTag      overrides the (ASMI) ad tag for the channel
  * @param definesVideoAdTag overrides the (ASMI) video ad tag for the channel
  */
case class RawChannelCommercial(definesAdTag: Option[Boolean] = None,
                                definesVideoAdTag: Option[Boolean] = None) {
  lazy val unwrappedDefinesAdTag: Boolean = definesAdTag.getOrElse(false)
  lazy val unwrappedDefinesVideoAdTag: Boolean = definesVideoAdTag.getOrElse(false)
}

/**
  * @param title         override `<title>` tag.
  * @param description   override `<meta name="description">` tag.
  * @param keywords      override `<meta name="keywords">` tag.
  * @param contentRobots override `<meta name="robots">` tag only for all content pages of the channel.
  * @param sectionRobots override `<meta name="robots">` tag only for the section page of the channel.
  */
case class RawChannelMetaTags(title: Option[String] = None,
                              description: Option[String] = None,
                              keywords: Option[Seq[String]] = None,
                              contentRobots: Option[RawChannelMetaRobotsTag] = None,
                              sectionRobots: Option[RawChannelMetaRobotsTag] = None) {
  lazy val unwrappedKeywords: Seq[String] = keywords.getOrElse(Nil)
}

/**
  * <meta name="robots" content="index,follow,noodp">
  *
  * @param noIndex  `true` == 'noIndex' & `false` == 'index'
  * @param noFollow `true` == 'noFollow' & `false` == 'follow'
  */
case class RawChannelMetaRobotsTag(noIndex: Option[Boolean] = None, noFollow: Option[Boolean] = None)

/**
  * Render a simple `<a>`.
  *
  * @param label display name of the link.
  * @param path  path (`href`) of the link.
  */
case class RawSectionReference(label: Option[String] = None, path: Option[String] = None)

/**
  *
  * @param sponsoring        only a mapping string for the client. Used for a svg/image logo. E.g. 'tagheuer'
  * @param logo              only a mapping string for the client. Used for a svg/image logo to replace the label.
  *                          The logo could be a channel logo like '/icon' or a ressort logo like '/kmpk'.
  *                          What's the different? Ask UI/UX!
  *                          Display-Logic:
  *                          channelLogo.orElse(ressortLogo).getOrElse(label)
  * @param slogan            slogan for the channel. E.g. /kmpkt: 'NEWS TO GO. EINZIGARTIG ANDERS.'
  * @param label             display name of the channel. The fallback label is always the [[RawChannelId.label]]
  * @param sectionReferences some optional links inside the header. Example: Link to a sub-channel.
  */
case class RawChannelHeader(sponsoring: Option[String] = None,
                            logo: Option[String] = None,
                            slogan: Option[String] = None,
                            label: Option[String] = None,
                            sectionReferences: Option[Seq[RawSectionReference]] = None)

/**
  * @param changedBy        id of last sitebuilder
  * @param lastModifiedDate timestamp of last change
  * @param modified         was this channel configured via ConfigMcConfigFace or is it still like `default`
  * @param isRessort        so far /icon, maybe blau and bilanz will be added (used for tree logic in angular app)
  */
case class Metadata(changedBy: String = "system",
                    lastModifiedDate: Long = Instant.now.toEpochMilli,
                    modified: Boolean = false,
                    isRessort: Boolean = false)


trait RawChannelStage {
  val `type`: String
  val index: Int
  val label: String
  val references: Option[Seq[RawSectionReference]] = None
  val teaserLimit: Option[Int] = None

  lazy val unwrappedReferences: Seq[RawSectionReference] = references.getOrElse(Nil)
}

/**
  * @param index          index of the stage (ordering)
  * @param label          display name of the stage
  * @param references     optional section references. Example: Link to Mediathek A-Z.
  * @param teaserLimit    todo harry
  * @param sourceOverride the default source is always the current channel path. This is a override.
  * @param desktopLayout  mapping string for a (desktop) layout name. The mapping is for Digger and Clients.
  *                       Why desktop and not mobile?
  *                       On a mobile device all teasers inside a stage are among each another. Only the desktop
  *                       breakpoint need some 'hints' to structure the teasers. Example: 1/3 1/3 1/3 teaser row.
  */
case class RawChannelStageContent(index: Int,
                                  label: String,
                                  override val references: Option[Seq[RawSectionReference]] = None,
                                  override val teaserLimit: Option[Int] = None,
                                  sourceOverride: Option[String] = None,
                                  desktopLayout: Option[String] = None) extends RawChannelStage {
  override val `type`: String = "content"
}

/**
  * todo harry: WTF is a module?
  *
  * @param index       index of the stage (ordering)
  * @param label       display name of the stage
  * @param references  optional section references. Example: Link to Mediathek A-Z.
  * @param teaserLimit todo harry
  * @param module      todo harry
  */
case class RawChannelStageModule(index: Int,
                                 label: String,
                                 override val references: Option[Seq[RawSectionReference]] = None,
                                 override val teaserLimit: Option[Int] = None,
                                 module: Option[String] = None) extends RawChannelStage {
  override val `type`: String = "module"
}

/**
  * @param index      index of the stage (ordering)
  * @param label      display name of the stage
  * @param references optional section references. Example: Link to Mediathek A-Z.
  * @param commercial mapping string with the name of the commercial. E.g. MediumRectangle
  */
case class RawChannelStageCommercial(index: Int,
                                     label: String,
                                     override val references: Option[Seq[RawSectionReference]] = None,
                                     commercial: Option[String] = None) extends RawChannelStage {
  override val `type`: String = "commercial"
  override val teaserLimit: Option[Int] = None
}
