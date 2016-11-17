package de.welt.contentapi.core.models.pressed

import de.welt.contentapi.core.models.content.ApiSectionReference

case class ApiStage(teasers: Seq[ApiTeaser],
                    configuration: Option[ApiStageConfiguration] = None)

case class ApiStageConfiguration(layout: String,
                                 label: Option[String],
                                 sectionReference: Option[Seq[ApiSectionReference]] = None)

