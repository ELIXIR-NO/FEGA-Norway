package validator

import (
	"fmt"
	"github.com/santhosh-tekuri/jsonschema/v5"
	"log"
	"os"
	"path/filepath"
	"strings"
)

// mapping between message "type" and JSON schema file (excluding suffix)
var schemaMapping = map[string]string{
	"accession":          "ingestion-accession",
	"contact.updated":    "user-contact-updated",
	"dac":                "dac-information",
	"dac.dataset":        "dac-dataset-mapping",
	"dac.members":        "dac-members",
	"deprecate":          "dataset-deprecate",
	"ingest":             "ingestion-trigger",
	"key_rotation":       "rotate-key",
	"keys.updated":       "user-keys-updated",
	"mapping":            "dataset-mapping",
	"password.updated":   "user-password-updated",
	"permission":         "dataset-permission",
	"permission.deleted": "dataset-permission-deleted",
	"release":            "dataset-release",
	"verified":           "ingestion-verification",
}

type JSONValidator struct {
	schemas map[string]*jsonschema.Schema
}

type ValidationError struct {
	Message     string
	SchemaError error
}

func (err ValidationError) Error() string {
	return "JSON message validation error: " + err.Message
}

func (v *JSONValidator) importSchemas(folder string) {
	log.Printf("Importing JSON schemas from folder: %s\n", folder)
	compiler := jsonschema.NewCompiler()
	compiler.Draft = jsonschema.Draft7

	v.schemas = make(map[string]*jsonschema.Schema)

	entries, err := os.ReadDir(folder)
	if err != nil {
		log.Fatalf("Error while reading JSON schemas: %v", err)
	}

	for _, entry := range entries {
		if strings.HasSuffix(entry.Name(), ".json") {
			schemaName := strings.TrimSuffix(entry.Name(), ".json")
			schemaPath := filepath.Join(folder, entry.Name())
			schema, err := compiler.Compile(schemaPath)
			if err != nil {
				log.Printf(" - WARNING: an error occurred while compiling JSON schema '%s' => %s\n", schemaPath, err)
			} else {
				log.Printf(" - Compiling JSON schema: %s\n", schemaPath)
				v.schemas[schemaName] = schema
			}
		}
	}
	// check if all schemas for known types have been loaded
	for messageType, schemaName := range schemaMapping {
		if _, ok := v.schemas[schemaName]; !ok {
			log.Printf(" - WARNING: schema '%s.json' for message type '%s' has not been loaded\n", schemaName, messageType)
		}
	}
}

func NewJSONValidator(schemafolder string) *JSONValidator {
	v := new(JSONValidator)
	if schemafolder != "" {
		v.importSchemas(schemafolder)
	} else {
		log.Printf("WARNING: No JSON schema folder specified. Skipping all message validations\n")
	}
	return v
}

func (v *JSONValidator) validateAgainstAllSchemas(message map[string]any) []string {
	matching := make([]string, 0, 1)
	for schemaName, schema := range v.schemas {
		if err := schema.Validate(message); err == nil {
			matching = append(matching, schemaName)
		}
	}
	return matching
}

func (v *JSONValidator) Validate(message map[string]any) error {
	if v == nil || v.schemas == nil {
		return nil
	}

	messageType, _ := message["type"].(string)
	if messageType != "" {
		schemaName, ok := schemaMapping[messageType]
		if ok {
			schema, ok := v.schemas[schemaName]
			if !ok { // check if schema for specified type is loaded
				errorMessage := fmt.Sprintf("Schema '%s.json' for message type '%s' not loaded", schemaName, messageType)
				return ValidationError{Message: errorMessage}
			}
			if err := schema.Validate(message); err != nil {
				errorMessage := fmt.Sprintf("Message of type '%s' failed validation against JSON schema '%s': %v", messageType, schemaName, err)
				return ValidationError{Message: errorMessage, SchemaError: err}
			} else {
				log.Printf("Message validation: Message of type '%s' is valid against JSON schema '%s'", messageType, schemaName)
			}
		} else { // no schema found for the message type
			errorMessage := fmt.Sprintf("No JSON schema found for message type '%s'", messageType)
			return ValidationError{Message: errorMessage}
		}
	} else { // Message does not contain a 'type' field. Try validating against all schemas
		matches := v.validateAgainstAllSchemas(message)
		if len(matches) > 0 {
			log.Printf("Message validation: Untyped message validated against the following schemas: %s", strings.Join(matches, ", "))
		} else {
			errorMessage := fmt.Sprintf("Untyped message did not validate against any schemas")
			return ValidationError{Message: errorMessage}
		}
	}
	return nil
}
