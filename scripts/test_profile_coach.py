import runpy
import unittest
from pathlib import Path

parse_frames = runpy.run_path(str(Path(__file__).with_name('profile-coach.py')))['parse_frames']


class FrameParserTest(unittest.TestCase):
    def test_nonzero_flags_are_missing_rows_not_zero_jank(self):
        result = parse_frames('Total frames rendered: 20\nJanky frames: 5 (25%)\n95th percentile: 150ms\n---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted\n2,1000000,201000000\n---PROFILEDATA---')
        self.assertEqual(result['janky'], 5)
        self.assertEqual(result['p95Ms'], 150)
        self.assertEqual(result['flags'], {'2': 1})
        self.assertEqual(result['validSamples'], 0)
        self.assertIsNone(result['over100Ms'])

    def test_valid_rows_count_long_frames_and_ignore_sentinels(self):
        result = parse_frames('---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted\n0,1000000,201000000\n0,0,9223372036854775807\n0,1000000,17000000\n---PROFILEDATA---')
        self.assertEqual(result['validSamples'], 2)
        self.assertEqual(result['over100Ms'], 1)
        self.assertEqual(result['maxMs'], 200)


if __name__ == '__main__':
    unittest.main()
