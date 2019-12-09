from pydub import AudioSegment


# Converts file from path to mp3
def convert(path_from, path_where):
    AudioSegment.from_file(path_from).export(path_where, format='mp3')
