# Orbital Dynamics Security Policy

## Passwords and authentication

All employees must use a password manager. Passwords must be at least 14 characters long and must never be reused across systems. Multi-factor authentication is mandatory for email, the VPN and the source code platform. Hardware security keys are issued to all engineers during onboarding.

## Incident reporting

Any suspected security incident must be reported to the security team at security@orbitaldynamics.example within 2 hours of discovery. Do not attempt to investigate or delete evidence yourself. The security team will open an incident ticket with the prefix SEC- and assign a severity from S1 (critical) to S4 (low).

## Data retention

Customer data is retained for 24 months after contract termination and then permanently deleted. Application logs are kept for 90 days. Database backups are encrypted with AES-256 and retained for 35 days.

## Laptop and device rules

Company laptops must use full-disk encryption. A lost or stolen device must be reported immediately so it can be remotely wiped. Personal devices may not be used to access production systems.

## Vulnerability disclosure

External researchers can report vulnerabilities through the public disclosure program. Critical findings are acknowledged within 24 hours and fixed within 7 days. Rewards range from 100 to 5000 euros depending on severity.
