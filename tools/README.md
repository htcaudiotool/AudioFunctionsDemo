# Analyzing Tools
## Installation
### Requirements
- pip
- Python 2.7
- virtualenv

#### Create a virtual environment
```bash
{WORK_DIR}$ mkdir venv
{WORK_DIR}$ virtualenv -p python2 venv/py2
Running virtualenv with interpreter /usr/bin/python2
New python executable in {WORK_DIR}/venv/py2/bin/python2
Also creating executable in {WORK_DIR}/venv/py2/bin/python
Installing setuptools, pip, wheel...done.
{WORK_DIR}$ source venv/py2/bin/activate
(py2) {WORK_DIR}$
```

#### The dependencies should be installed with a command
```bash
(py2) {WORK_DIR}$ pip install -r requirements.txt
```

## `analyze_record_dump.py`

### Enable recording for start bufferring the data
```bash
(py2) {WORK_DIR}$ adb broadcast -a audio.htc.com.intent.record.start
```

### Try to call dump command via adb intent
```bash
(py2) {WORK_DIR}$ adb broadcast -a audio.htc.com.intent.record.dump --es path record-dump
```

### After the dump is completed, try to parse it via the tool
```bash
(py2) {WORK_DIR}$ adb pull sdcard/PyAAT/record-dump ./
sdcard/PyAAT/record-dump/: 3 files pulled. 0 files skipped. 10.0 MB/s (1082617 bytes in 0.103s)
(py2) {WORK_DIR}$ tree record-dump
record-dump/
├── info.json
└── stream.bin

0 directories, 2 files
(py2) {WORK_DIR}$ python tools/analyze_record_dump.py record-dump
(py2) {WORK_DIR}$ tree record-dump
record-dump/
├── info.json
├── parse_config.json
├── pcmdump.wav
├── signal.png
├── spectrogram.png
└── stream.bin

0 directories, 6 files
```

Then four files are generated, namely the parse configuration, the signal waveform, the spectrogram, and the pcm hearable file. Some configuration parameters are adjustable:

### `parse_config.json`
```json
# Parse Configuration
# The record dump starts at "2018-03-14 15:21:00.084 (UTF+8)"
# Dump information:
#     - sampling frequency  : 8000.0 Hz
#     - pcm dump duration   : 48.644 sec.
#     - record buffer length: 40.0 ms.
# Description of the fields:
#     - pcm     : the pcm dump (.wav) from the timestamp after the offset
#        - "from" and "to" are the signal range configuration for the output audio file with respect to sec.
#     - signal  : the signal waveform (.png) from the timestamp after the offset
#        - x-axis refers to the time index with respect to sec.
#        - y-axis refers to the amplitude
#     - spectrogram: the spectrogram (.png) from the timestamp after the offset
#        - x-axis refers to the frame index with respect to a 40.0ms-length signal frame
#        - y-axis refers to the frequency with respect to Hz

{
    "spectrogram": {
        "ylim": [
            -7.8125, 
            3992.1875
        ], 
        "xlim": [
            -0.5, 
            1216.5
        ]
    }, 
    "signal": {
        "horiz_extent_ratio": 1.0, 
        "ylim": [
            -0.902198275862069, 
            0.9858189655172414
        ], 
        "xlim": [
            -2.43219375, 
            51.076068750000005
        ]
    }, 
    "version": "1.0.0", 
    "pcm": {
        "to": 48.644, 
        "from": 0
    }
}
```