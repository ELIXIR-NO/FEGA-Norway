package plan

import (
	"reflect"
	"testing"
)

func TestChunks(t *testing.T) {
	tests := []struct {
		name                              string
		size, offset, chunkSize, firstNum int64
		want                              []Chunk
	}{
		{name: "size 0", size: 0, chunkSize: 10, firstNum: 1, want: nil},
		{name: "size below chunk", size: 7, chunkSize: 10, firstNum: 1, want: []Chunk{{1, 0, 7}}},
		{name: "exact multiple", size: 30, chunkSize: 10, firstNum: 1, want: []Chunk{{1, 0, 10}, {2, 10, 10}, {3, 20, 10}}},
		{name: "remainder", size: 25, chunkSize: 10, firstNum: 1, want: []Chunk{{1, 0, 10}, {2, 10, 10}, {3, 20, 5}}},
		{name: "offset mid file", size: 25, offset: 13, chunkSize: 10, firstNum: 1, want: []Chunk{{1, 13, 10}, {2, 23, 2}}},
		{name: "first num", size: 25, offset: 10, chunkSize: 10, firstNum: 4, want: []Chunk{{4, 10, 10}, {5, 20, 5}}},
		{name: "offset at end", size: 25, offset: 25, chunkSize: 10, firstNum: 3, want: nil},
		{name: "offset past end", size: 25, offset: 30, chunkSize: 10, firstNum: 3, want: nil},
		{name: "chunk size 0", size: 25, chunkSize: 0, firstNum: 1, want: nil},
		{name: "negative chunk size", size: 25, chunkSize: -1, firstNum: 1, want: nil},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := Chunks(tt.size, tt.offset, tt.chunkSize, tt.firstNum)
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Chunks(%d, %d, %d, %d) = %v, want %v", tt.size, tt.offset, tt.chunkSize, tt.firstNum, got, tt.want)
			}
		})
	}
}
