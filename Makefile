.PHONY: help test fmt run clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

test: ## Run all tests
	clojure -M:test

fmt: ## Format all Clojure source files
	clj-paren-repair src/ test/

run: ## Run samplanager (usage: make run DIR=/path/to/samples OUT=duplicates.json)
	clojure -M -m samplanager.core $(DIR) $(OUT)

clean: ## Clean caches
	rm -rf .cpcache target
