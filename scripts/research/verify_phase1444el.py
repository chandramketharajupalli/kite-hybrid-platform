"""Recovery cannot be certified while containment and dynamic gates are pending."""


def verify() -> None:
    raise RuntimeError("INCOMPLETE_PROCESS_CONTAINMENT_AND_VALIDATION")


if __name__ == "__main__":
    verify()
