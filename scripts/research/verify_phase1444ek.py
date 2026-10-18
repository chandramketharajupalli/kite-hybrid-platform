"""Incomplete recovery cannot be certified; no native supervisor imports."""


def verify() -> None:
    raise RuntimeError("RECOVERY_INCOMPLETE_CAPTURE_PARSE_FAILURE")


if __name__ == "__main__":
    verify()
