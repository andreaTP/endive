;; Calls the owner's entry as the supertype.
(module
  (type $super (sub (func (result funcref))))
  (import "owner" "tab" (table 1 funcref))

  (func (export "call") (result i32)
    (ref.is_null (call_indirect (type $super) (i32.const 0))))
)
