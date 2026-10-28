;; Calls the owner's multi-value entry and drops both results.
(module
  (type $pair (func (result i32 i32)))
  (import "owner" "tab" (table 1 funcref))

  (func (export "call")
    (call_indirect (type $pair) (i32.const 0))
    drop
    drop)
)
