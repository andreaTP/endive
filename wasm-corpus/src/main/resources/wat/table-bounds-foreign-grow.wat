;; Grows its own table with the owner's entry, then calls through a grown slot.
(module
  (import "owner" "tab" (table 1 funcref))
  (table $own 1 funcref)
  (type $void (func))

  (func $local)

  (func (export "grow") (result i32)
    (table.grow $own (table.get 0 (i32.const 0)) (i32.const 2)))

  (func (export "callGrown")
    (call_indirect $own (type $void) (i32.const 1)))
)
