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

  @Test fun threeWindowsRetainTheirOwnIndependentSpectralMeasurements() {
    val raw = buildString {
      for (frame in 0 until 36 * 50) {
        val timestamp = frame / 50.0
        val centroid = when {
          timestamp < 12.0 -> 5500
          timestamp < 24.0 -> 1700
          else -> 450
        }
        append("frame:").append(frame).append(" pts_time:").append(timestamp).append('\n')
        append("lavfi.aspectralstats.1.centroid=").append(centroid).append('\n')
        append("lavfi.aspectralstats.1.flatness=0.27\n")
        append("lavfi.aspectralstats.1.rolloff=10000\n")
        append("lavfi.astats.Overall.Peak_level=-3\n")
        append("lavfi.astats.Overall.RMS_level=-25\n")
      }
    }
    val report = StreamAstatsParser.parse(
      raw, listOf(0.0 to 10.0, 12.0 to 22.0, 24.0 to 34.0),
    )
    assertEquals(3, report.segments.size)
    assertEquals(5500.0, report.segments[0].spectralCentroidHz ?: 0.0, 0.01)
    assertEquals(1700.0, report.segments[1].spectralCentroidHz ?: 0.0, 0.01)
    assertEquals(450.0, report.segments[2].spectralCentroidHz ?: 0.0, 0.01)
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
  fun partialWindowCannotBePresentedAsFullTenSecondSample() {
    // v44 accepted 55% of a requested window. A 6s fragment is not 10s.
    val raw = syntheticStats(6)
    StreamAstatsParser.parse(raw, listOf(0.0 to 10.0))
  }

  @Test(expected = IllegalArgumentException::class)
  fun midWindowPtsGapCannotCountAsContinuousAudio() {
    val raw = buildString {
      for (frame in 0 until 15 * 50) {
        if (frame in 250 until 500) continue // Missing 5 seconds in the middle.
        append("frame:").append(frame).append(" pts_time:").append(frame / 50.0).append('\n')
        append("lavfi.astats.Overall.Peak_level=-2\n")
        append("lavfi.astats.Overall.RMS_level=-24\n")
      }
    }
    StreamAstatsParser.parse(raw, listOf(0.0 to 15.0))
  }

  @Test(expected = IllegalArgumentException::class)
  fun nonMonotonicPtsRejectsStreamDiscontinuity() {
    val forward = syntheticStats(15)
    val restarted = syntheticStats(15)
    StreamAstatsParser.parse(forward + restarted, listOf(0.0 to 15.0))
  }

  @Test(expected = IllegalArgumentException::class)
  fun sampleMissingBeginningCannotBeAcceptedByLateFrames() {
    val raw = buildString {
      for (frame in 50 until 15 * 50) {
        append("frame:").append(frame).append(" pts_time:").append(frame / 50.0).append('\n')
        append("lavfi.astats.Overall.Peak_level=-2\n")
        append("lavfi.astats.Overall.RMS_level=-24\n")
      }
    }
    // Start first sample at 1 sec, then a missing part of requested 10-second span.
    StreamAstatsParser.parse(raw, listOf(0.0 to 15.0))
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
