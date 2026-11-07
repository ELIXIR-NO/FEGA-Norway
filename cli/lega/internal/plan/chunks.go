package plan

// Chunks splits the bytes of a file from offset to size into chunks of at
// most chunkSize bytes, numbered from firstNum. Sizes and offsets are in
// bytes. It returns nil when there is nothing to send or chunkSize is not
// positive, so it can never loop forever.
func Chunks(size, offset, chunkSize, firstNum int64) []Chunk {
	if chunkSize <= 0 || offset >= size {
		return nil
	}
	chunks := make([]Chunk, 0, chunkCount(size-offset, chunkSize))
	for num := firstNum; offset < size; num++ {
		n := min(chunkSize, size-offset)
		chunks = append(chunks, Chunk{Num: num, Offset: offset, Len: n})
		offset += n
	}
	return chunks
}

func chunkCount(remaining, chunkSize int64) int64 {
	return (remaining + chunkSize - 1) / chunkSize
}
