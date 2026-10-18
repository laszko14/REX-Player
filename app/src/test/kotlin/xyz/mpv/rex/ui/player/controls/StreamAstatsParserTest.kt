package xyz.mpv.rex.ui.player.controls.components.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAstatsParserTest {
  private fun syntheticStats(seconds: Int): String = buildString {
    for (frame in 0 until seconds * 50) {
      val t = frame / 50.0
      append("frame:").append(frame).append("    pts:").append(frame * 960)
        .append("    pts_time:").append(t).append('\n')
      append("lavfi.astats.Overall.Peak_level=-1.2\n")
      append("lavfi.astats.Overall.RMS_level=")
        .append(if (frame % 50 < 8) "-59.0" else "-23.0").append('\n')
    }
  }

  @Test fun detailedModeReadsRealSpectralMetadata() {
    val audio = buildString {
      for (frame in 0 until 36 * 50) {
        append("frame:").append(frame).append(" pts_time:").append(frame / 50.0).append('\n')
        append("lavfi.aspectralstats.1.centroid=4200\n")
        append("lavfi.aspectralstats.1.flatness=0.32\n")
        append("lavfi.aspectralstats.1.rolloff=11000\n")
        append("lavfi.astats.Overall.Peak_level=-2\n")
        append("lavfi.astats.Overall.RMS_level=-22\n")
      }
    }
    val metrics = StreamAstatsParser.parse(
      audio, listOf(0.0 to 10.0, 12.0 to 22.0, 24.0 to 34.0),
    )
    assertEquals(4200.0, metrics.spectralCentroidHz ?: 0.0, 0.001)
    assertEquals(0.32, metrics.spectralFlatness ?: 0.0, 0.001)
    assertEquals(11000.0, metrics.spectralRolloffHz ?: 0.0, 0.001)
  }

  @Test fun quickModeReadsOnlyFifteenSeconds() {
    val report = StreamAstatsParser.parse(syntheticStats(36), listOf(0.0 to 15.0))
    assertEquals(15.0, report.seconds, 0.001)
    assertTrue(report.rmsDb < -15.0)
    assertTrue(report.noiseFloorDb < -20.0)
    assertTrue(report.peak > 0.8)
    assertEquals(0, report.channelCount)
  }

  @Test fun detailedModeSamplesThreeWindowsWithoutSeeking() {
    val report = StreamAstatsParser.parse(
      syntheticStats(36),
      listOf(0.0 to 10.0, 12.0 to 22.0, 24.0 to 34.0),
    )
    assertEquals(30.0, report.seconds, 0.001)
  }

  @Test(expected = IllegalArgumentException::class)
  fun missingMetadataCannotPretendToHaveAnalysedAudio() {
    StreamAstatsParser.parse("frame:1 pts_time:0", listOf(0.0 to 15.0))
  }

  @Test(expected = IllegalArgumentException::class)
  fun incompleteThirdWindowIsRejected() {
    StreamAstatsParser.parse(
      syntheticStats(23),
      listOf(0.0 to 10.0, 12.0 to 22.0, 24.0 to 34.0),
    )
  }
}
