package conf

import (
	"testing"
)

func TestNewConfigurationSameInstance(t *testing.T) {
	t.Setenv("CENTRAL_EGA_USERNAME", "1")
	t.Setenv("CENTRAL_EGA_PASSWORD", "2")
	t.Setenv("ELIXIR_AAI_TOKEN", "3")
	t.Setenv("TSD_PROJ_NAME", "5")
	t.Setenv("TSD_SERV", "6")
	configuration1 := NewConfiguration()
	configuration2 := NewConfiguration()
	if configuration1 != configuration2 {
		t.Error()
	}
}

func TestGetCentralEGAUsername(t *testing.T) {
	t.Setenv("CENTRAL_EGA_USERNAME", "1")
	configuration := NewConfiguration()
	if configuration.GetCentralEGAUsername() != "1" {
		t.Error()
	}
}

func TestGetCentralEGAPassword(t *testing.T) {
	t.Setenv("CENTRAL_EGA_PASSWORD", "2")
	configuration := NewConfiguration()
	if configuration.GetCentralEGAPassword() != "2" {
		t.Error()
	}
}

func TestGetElixirAAIToken(t *testing.T) {
	t.Setenv("ELIXIR_AAI_TOKEN", "3")
	configuration := NewConfiguration()
	if configuration.GetElixirAAIToken() != "3" {
		t.Error()
	}
}

func TestGetTSDAPIVersion(t *testing.T) {
	configuration := NewConfiguration()
	if configuration.GetTSDAPIVersion() != "v1" {
		t.Error()
	}
}

func TestGetTSDProjectName(t *testing.T) {
	t.Setenv("TSD_PROJ_NAME", "5")
	configuration := NewConfiguration()
	if configuration.GetTSDProjectName() != "5" {
		t.Error()
	}
}

func TestGetTSDservice(t *testing.T) {
	configuration := NewConfiguration()
	if configuration.GetTSDservice() != "ega" {
		t.Error()
	}
}

func TestNewConfigurationDefaultInstanceURL(t *testing.T) {
	t.Setenv("LOCAL_EGA_INSTANCE_URL", "")
	configuration := NewConfiguration()
	if configuration.GetLocalEGAInstanceURL() != defaultInstanceURL {
		t.Error()
	}
}

func TestNewConfigurationNonDefaultInstanceURL(t *testing.T) {
	t.Setenv("LOCAL_EGA_INSTANCE_URL", "test/")
	configuration := NewConfiguration()
	if configuration.GetLocalEGAInstanceURL() != "test" {
		t.Error()
	}
}

func TestNewConfigurationDefaultTSDbaseURL(t *testing.T) {
	t.Setenv("TSD_BASE_URL", "")
	configuration := NewConfiguration()
	if configuration.GetTSDbaseURL() != defaultTSDfileAPIbaseURL {
		t.Error()
	}
}

func TestNewConfigurationNonDefaultTSDbaseURL(t *testing.T) {
	t.Setenv("TSD_BASE_URL", "test/tsd_base/")
	configuration := NewConfiguration()
	if configuration.GetTSDbaseURL() != "test/tsd_base" {
		t.Error()
	}
}

func TestNewConfigurationDefaultChunkSize(t *testing.T) {
	t.Setenv("LEGA_COMMANDER_CHUNK_SIZE", "")
	configuration := NewConfiguration()
	if configuration.GetChunkSize() != defaultChunkSize {
		t.Error()
	}
}

func TestNewConfigurationNonDefaultChunkSize(t *testing.T) {
	t.Setenv("LEGA_COMMANDER_CHUNK_SIZE", "100")
	configuration := NewConfiguration()
	if configuration.GetChunkSize() != 100 {
		t.Error()
	}
}

func TestNewConfigurationNonNumericChunkSize(t *testing.T) {
	t.Setenv("LEGA_COMMANDER_CHUNK_SIZE", "test")
	configuration := NewConfiguration()
	if configuration.GetChunkSize() != defaultChunkSize {
		t.Error()
	}
}

func TestNewConfigurationGetTSDURL(t *testing.T) {
	t.Setenv("TSD_BASE_URL", "tsd_base/")
	t.Setenv("TSD_PROJ_NAME", "tsd_project")
	configuration := NewConfiguration()
	if configuration.GetTSDURL() != "tsd_base/v1/tsd_project/ega" {
		t.Error()
	}
	t.Setenv("TSD_PROJ_NAME", "")
	configuration = NewConfiguration()
	if configuration.GetTSDURL() != "tsd_base/v1/p969/ega" {
		t.Error()
	}
}
