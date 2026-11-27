package tsd

import (
	"errors"
	"fmt"

	"github.com/golang-jwt/jwt/v5"
)

// userClaim reads the user claim from a TSD access token. The signature is
// not checked: TSD verifies the token, and the client only needs the claim
// to build the upload path.
func userClaim(token string) (string, error) {
	claims := jwt.MapClaims{}
	if _, _, err := jwt.NewParser().ParseUnverified(token, claims); err != nil {
		return "", fmt.Errorf("read TSD token: %w", err)
	}
	user, ok := claims["user"].(string)
	if !ok || user == "" {
		return "", errors.New("TSD token has no user claim")
	}
	return user, nil
}
